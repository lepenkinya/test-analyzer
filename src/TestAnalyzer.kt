import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.dom.asList

data class TeamCityReportData(val fullTestName: String, val success: Boolean, val time: Int)
data class TestData(val name: String, var duration: Int)

fun Int.millisToMinutes() = this / 1000 / 60

fun extractTeamCityReportData(fileName: String): List<TeamCityReportData> {
    val lines = File(fileName).readLines().drop(1)
    return lines
            .map {
                val i = it.indexOf(": ")
                if (i < 0) {
                    trimTestNumber(it)
                } else {
                    it.substring(i + 2)
                }
            }
            .filterNot { it.startsWith("_") }
            .map {
                var (name, result, time) = extractTestData(it)
                if (name.contains("$")) {
                    name = name.substringBefore("$")
                }

                TeamCityReportData(name, result.equals("OK"), time.toInt())
            }
}

fun trimTestNumber(data: String): String {
    val i = data.indexOf(",")
    return data.substring(i + 1, data.length())
}

fun extractTestData(data: String): Triple<String, String, String> {
    var list: MutableList<String> = arrayListOf()

    var tmp = data;
    var previousCommaIndex = 0
    for (i in 0..1) {
        previousCommaIndex = tmp.lastIndexOf(",")
        list.add(tmp.substring(previousCommaIndex + 1, tmp.length()))
        tmp = data.substring(0, previousCommaIndex)
    }
    list.add(data.substring(0, previousCommaIndex))

    return Triple(list.get(2), list.get(1), list.get(0))
}


fun main(args: Array<String>) {
    val tcReports = extractTeamCityReportData("AllTests.csv")

    val testData = convertToTestData(tcReports)

    val totalTime = tcReports.sumBy { it.time }
    val newTotalTime = testData.sumBy { it.duration }


    val buckets = distributeToBuckets(4, testData)

    writeToFiles(buckets)

    println ("Total ${totalTime.millisToMinutes() / 60} h")
    println ("Total ${newTotalTime.millisToMinutes() / 60} h")
}

fun convertToTestData(tcReports: List<TeamCityReportData>): List<TestData> {

    val testData: MutableList<TestData> = arrayListOf()

    tcReports.map {
        val className = extractTestClassName(it.fullTestName)
        TeamCityReportData(className, it.success, it.time)
    }.forEach {
        if (testData.isEmpty() || !it.fullTestName.equals(testData.last().name)) {
            testData.add(TestData(it.fullTestName, it.time))
        } else {
            val last = testData.last()
            last.duration += it.time
        }
    }

    return testData
}

fun extractTestClassName(fullTestName: String): String {
    var dot = fullTestName.indexOf(".")

    while (fullTestName.charAt(dot + 1).isLowerCase()) {
        dot = fullTestName.indexOf(".", dot + 1)
    }

    val nextDot = fullTestName.indexOf(".", dot + 1)
    return fullTestName.substring(0, if (nextDot > 0) nextDot else fullTestName.length())
}

private fun writeToFiles(buckets: List<List<TestData>>) {
    var i = 0;
    val dir = File("result")
    dir.mkdir()

    buckets.forEach {
        val file = File("result/$i")
        it.forEach {
            file.appendText("${it.name}||")
        }

        val executionTime = it.sumBy { it.duration }.millisToMinutes()
        println("Time for: $i : $executionTime mins")

        i++;
    }

    val bucketsExecutionTime = buckets.map { it.sumBy { it.duration } }.sort()
    println("Min time: ${bucketsExecutionTime.first().millisToMinutes()} Max time: ${bucketsExecutionTime.last().millisToMinutes()}")
}

private fun distributeToBuckets(bucketsNumber: Int, testData: List<TestData>): List<List<TestData>> {
    val buckets: MutableList<MutableList<TestData>> = arrayListOf()
    for (i in 1..bucketsNumber) {
        buckets.add(arrayListOf())
    }

    testData.forEach {
        val sorted = buckets.sortBy { it.sumBy { it.duration } }
        sorted.get(0).add(it)
    }

    return buckets
}
