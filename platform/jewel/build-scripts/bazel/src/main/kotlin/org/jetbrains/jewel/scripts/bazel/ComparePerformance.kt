@file:Suppress("RAW_RUN_BLOCKING", "SSBasedInspection")

package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Run it like: bazel run //platform/jewel/build-scripts/bazel:comparePerformance -- baseline current
fun main(args: Array<String>) {
    runBlocking { ComparePerformanceCommand().main(args) }
}

internal class ComparePerformanceCommand(private val commandRunner: CommandRunner = DefaultCommandRunner) :
    SuspendingCliktCommand(name = "compare-performance") {

    private val baselinePath: String by argument().help("Baseline folder or JFR file")

    private val currentPath: String by argument().help("Current folder or JFR file")

    private val packageFilter: String by
        option("--package", help = "Package prefix to filter hotspot analysis (e.g., org.jetbrains.jewel)")
            .default("org.jetbrains.jewel")

    private val perTest: Boolean by
        option("--per-test", help = "Match and compare JFR files by test name (for --per-test mode results)")
            .flag(default = false)

    private val aggregateRuns: Boolean by
        option(
                "--aggregate-runs",
                help = "Aggregate multiple runs of the same test (e.g., test_run1.jfr, test_run2.jfr)",
            )
            .flag(default = false)

    override fun help(context: Context): String =
        """
        Compare two JFR profiling runs to assess performance changes.
        Supports both single JFR files and folders with multiple JFR files.
        When using folders, metrics are averaged across all runs.
        Use --per-test to compare matching test methods individually.
        Use --aggregate-runs with --per-test to aggregate multiple runs of the same test.

        File naming for --aggregate-runs:
          - test_run1.jfr, test_run2.jfr    -> grouped as "test"
          - test_run_1.jfr, test_run_2.jfr  -> grouped as "test"
          - test.run1.jfr, test.run2.jfr    -> grouped as "test"
          - test_1.jfr, test_2.jfr          -> grouped as "test"
        """
            .trimIndent()

    override suspend fun run() {
        printHeader()

        val baselineFile = resolvePath(baselinePath)
        val currentFile = resolvePath(currentPath)

        if (!baselineFile.exists()) {
            printlnErr("Baseline path not found: $baselinePath")
            exitWithError("Baseline path not found")
        }

        if (!currentFile.exists()) {
            printlnErr("Current path not found: $currentPath")
            exitWithError("Current path not found")
        }

        // Get JFR files
        val baselineFiles = getJfrFiles(baselineFile)
        val currentFiles = getJfrFiles(currentFile)

        if (baselineFiles.isEmpty()) {
            printlnErr("No JFR files found in baseline path: $baselinePath")
            exitWithError("No JFR files found in baseline")
        }

        if (currentFiles.isEmpty()) {
            printlnErr("No JFR files found in current path: $currentPath")
            exitWithError("No JFR files found in current")
        }

        if (perTest) {
            // Per-test comparison mode: match files by name and compare individually
            performPerTestComparison(baselineFiles, currentFiles)
        } else {
            // Original behavior: aggregate all files together
            performAggregatedComparison(baselineFiles, currentFiles)
        }
    }

    private suspend fun performPerTestComparison(baselineFiles: List<File>, currentFiles: List<File>) {
        println("Mode: ".asWarning() + "Per-test comparison")
        if (aggregateRuns) {
            println("Aggregation: ".asWarning() + "Multiple runs per test will be aggregated")
        }
        println()

        // Match files by name (with optional run aggregation)
        val baselineMap: Map<String, List<File>>
        val currentMap: Map<String, List<File>>

        if (aggregateRuns) {
            // Group files by base test name (removing _runN or .runN suffixes)
            baselineMap = baselineFiles.groupBy { extractBaseTestName(it.name) }
            currentMap = currentFiles.groupBy { extractBaseTestName(it.name) }
        } else {
            // Original behavior: one file per test
            baselineMap = baselineFiles.associateBy { it.name }.mapValues { listOf(it.value) }
            currentMap = currentFiles.associateBy { it.name }.mapValues { listOf(it.value) }
        }

        val matchedTests = (baselineMap.keys intersect currentMap.keys).sorted()
        val baselineOnly = (baselineMap.keys - currentMap.keys).sorted()
        val currentOnly = (currentMap.keys - baselineMap.keys).sorted()

        if (matchedTests.isEmpty()) {
            printlnErr("No matching test files found between baseline and current")
            exitWithError("No matching tests")
        }

        println("Matched tests: ".asSuccess() + "${matchedTests.size}")
        matchedTests.forEach { println("  ✓ $it") }
        println()

        if (baselineOnly.isNotEmpty()) {
            println("Only in baseline: ".asWarning() + "${baselineOnly.size}")
            baselineOnly.forEach { println("  - $it") }
            println()
        }

        if (currentOnly.isNotEmpty()) {
            println("Only in current: ".asWarning() + "${currentOnly.size}")
            currentOnly.forEach { println("  - $it") }
            println()
        }

        // Compare each matched test
        var totalImprovements = 0
        var totalRegressions = 0
        val testResults = mutableListOf<TestComparisonResult>()

        for ((index, testName) in matchedTests.withIndex()) {
            println("═══════════════════════════════════════════════")
            println("  [${index + 1}/${matchedTests.size}] $testName".asBold())
            println("═══════════════════════════════════════════════")
            println()

            val baselineFileList = baselineMap[testName]!!
            val currentFileList = currentMap[testName]!!

            // Extract metrics from all runs and aggregate
            val baselineMetrics = mutableListOf<Metrics>()
            val currentMetrics = mutableListOf<Metrics>()

            for (file in baselineFileList) {
                baselineMetrics.add(extractMetrics(file))
            }

            for (file in currentFileList) {
                currentMetrics.add(extractMetrics(file))
            }

            // Display run information if aggregating
            if (aggregateRuns && (baselineFileList.size > 1 || currentFileList.size > 1)) {
                println("Baseline runs: ${baselineFileList.size}".asWarning())
                baselineFileList.forEach { println("  - ${it.name}") }
                println()
                println("Current runs:  ${currentFileList.size}".asWarning())
                currentFileList.forEach { println("  - ${it.name}") }
                println()
            }

            // Calculate aggregated metrics
            val baselineMetric =
                if (baselineMetrics.size == 1) {
                    baselineMetrics.first()
                } else {
                    calculateAverageMetrics(baselineMetrics)
                }

            val currentMetric =
                if (currentMetrics.size == 1) {
                    currentMetrics.first()
                } else {
                    calculateAverageMetrics(currentMetrics)
                }

            // Display statistics if multiple runs
            if (aggregateRuns && baselineMetrics.size > 1) {
                println("Baseline Statistics:".asWarning())
                displayMetricStats("Duration (ms)", baselineMetrics.map { it.duration })
                displayMetricStats("CPU Samples", baselineMetrics.map { it.totalSamples.toLong() })
                displayMetricStats("Allocations", baselineMetrics.map { it.allocations.toLong() })
                displayMetricStats("GC Count", baselineMetrics.map { it.gcStats.totalGcCount.toLong() })
                displayMetricStats("Max GC Pause (ms)", baselineMetrics.map { it.gcStats.maxGcPauseTimeMs })
                displayMetricStats("Max Heap Used (MB)", baselineMetrics.map { it.heapSummary.maxHeapUsedMb })
                println()
            }

            if (aggregateRuns && currentMetrics.size > 1) {
                println("Current Statistics:".asWarning())
                displayMetricStats("Duration (ms)", currentMetrics.map { it.duration })
                displayMetricStats("CPU Samples", currentMetrics.map { it.totalSamples.toLong() })
                displayMetricStats("Allocations", currentMetrics.map { it.allocations.toLong() })
                displayMetricStats("GC Count", currentMetrics.map { it.gcStats.totalGcCount.toLong() })
                displayMetricStats("Max GC Pause (ms)", currentMetrics.map { it.gcStats.maxGcPauseTimeMs })
                displayMetricStats("Max Heap Used (MB)", currentMetrics.map { it.heapSummary.maxHeapUsedMb })
                println()
            }

            // Display basic metrics
            val durationChange = calculateChange(baselineMetric.duration, currentMetric.duration)
            val samplesChange =
                calculateChange(baselineMetric.totalSamples.toLong(), currentMetric.totalSamples.toLong())
            val allocationsChange =
                calculateChange(baselineMetric.allocations.toLong(), currentMetric.allocations.toLong())
            val gcCountChange =
                calculateChange(
                    baselineMetric.gcStats.totalGcCount.toLong(),
                    currentMetric.gcStats.totalGcCount.toLong(),
                )
            val maxGcPauseChange =
                calculateChange(baselineMetric.gcStats.maxGcPauseTimeMs, currentMetric.gcStats.maxGcPauseTimeMs)
            val maxHeapChange =
                calculateChange(baselineMetric.heapSummary.maxHeapUsedMb, currentMetric.heapSummary.maxHeapUsedMb)

            printMetric("Duration (ms)", baselineMetric.duration, currentMetric.duration, durationChange, true)
            printMetric(
                "CPU Samples",
                baselineMetric.totalSamples.toLong(),
                currentMetric.totalSamples.toLong(),
                samplesChange,
                true,
            )
            printMetric(
                "Allocations",
                baselineMetric.allocations.toLong(),
                currentMetric.allocations.toLong(),
                allocationsChange,
                true,
            )
            printMetric(
                "GC Count",
                baselineMetric.gcStats.totalGcCount.toLong(),
                currentMetric.gcStats.totalGcCount.toLong(),
                gcCountChange,
                true,
            )
            printMetric(
                "Max GC Pause (ms)",
                baselineMetric.gcStats.maxGcPauseTimeMs,
                currentMetric.gcStats.maxGcPauseTimeMs,
                maxGcPauseChange,
                true,
            )
            printMetric(
                "Max Heap (MB)",
                baselineMetric.heapSummary.maxHeapUsedMb,
                currentMetric.heapSummary.maxHeapUsedMb,
                maxHeapChange,
                true,
            )
            println()

            // Count improvements/regressions
            var testImprovements = 0
            var testRegressions = 0

            listOf(durationChange, samplesChange, allocationsChange).forEach { change ->
                if (change != null) {
                    when {
                        change < -10.0 -> testImprovements++
                        change > 10.0 -> testRegressions++
                    }
                }
            }

            totalImprovements += testImprovements
            totalRegressions += testRegressions

            val verdict =
                when {
                    testImprovements > testRegressions -> "IMPROVED".asSuccess()
                    testRegressions > testImprovements -> "REGRESSED".asError()
                    else -> "UNCHANGED".asWarning()
                }

            println("Test verdict: $verdict")
            println()

            testResults.add(
                TestComparisonResult(
                    testName = testName,
                    improvements = testImprovements,
                    regressions = testRegressions,
                    durationChange = durationChange,
                    samplesChange = samplesChange,
                    allocationsChange = allocationsChange,
                )
            )
        }

        // Overall summary
        println("═══════════════════════════════════════════════")
        println("  Overall Summary".asBold())
        println("═══════════════════════════════════════════════")
        println()

        val improved = testResults.count { it.improvements > it.regressions }
        val regressed = testResults.count { it.regressions > it.improvements }
        val unchanged = testResults.count { it.improvements == it.regressions }

        println("Tests analyzed: ${testResults.size}")
        println("  Improved:  $improved".asSuccess())
        println("  Regressed: $regressed".asError())
        println("  Unchanged: $unchanged".asWarning())
        println()

        println("Total metric changes:")
        println("  Improvements: $totalImprovements")
        println("  Regressions:  $totalRegressions")
        println()

        when {
            improved > regressed -> println("✅ Overall performance IMPROVED".asSuccess())
            regressed > improved -> println("⚠️  Overall performance REGRESSED".asError())
            else -> println("➡️  Overall performance UNCHANGED".asWarning())
        }
        println()

        // Show top regressions
        val topRegressions = testResults.filter { it.regressions > 0 }.sortedByDescending { it.regressions }.take(5)

        if (topRegressions.isNotEmpty()) {
            println("Top Regressions:".asError())
            topRegressions.forEach { result ->
                println("  ${result.testName}:")
                if (result.durationChange != null && result.durationChange > 10.0) {
                    println("    Duration: +${"%.1f".format(result.durationChange)}%")
                }
                if (result.samplesChange != null && result.samplesChange > 10.0) {
                    println("    CPU Samples: +${"%.1f".format(result.samplesChange)}%")
                }
                if (result.allocationsChange != null && result.allocationsChange > 10.0) {
                    println("    Allocations: +${"%.1f".format(result.allocationsChange)}%")
                }
            }
            println()
        }

        // Show top improvements
        val topImprovements = testResults.filter { it.improvements > 0 }.sortedByDescending { it.improvements }.take(5)

        if (topImprovements.isNotEmpty()) {
            println("Top Improvements:".asSuccess())
            topImprovements.forEach { result ->
                println("  ${result.testName}:")
                if (result.durationChange != null && result.durationChange < -10.0) {
                    println("    Duration: ${"%.1f".format(result.durationChange)}%")
                }
                if (result.samplesChange != null && result.samplesChange < -10.0) {
                    println("    CPU Samples: ${"%.1f".format(result.samplesChange)}%")
                }
                if (result.allocationsChange != null && result.allocationsChange < -10.0) {
                    println("    Allocations: ${"%.1f".format(result.allocationsChange)}%")
                }
            }
        }
    }

    private suspend fun performAggregatedComparison(baselineFiles: List<File>, currentFiles: List<File>) {
        // Display file information
        println("Baseline: ".asWarning() + "$baselinePath (${baselineFiles.size} file(s))")
        baselineFiles.forEach { println("  - ${it.name}") }
        println()

        println("Current:  ".asWarning() + "$currentPath (${currentFiles.size} file(s))")
        currentFiles.forEach { println("  - ${it.name}") }
        println()

        // Extract metrics with spinners
        println("Extracting metrics...".asWarning())
        println()

        val baselineMetrics = analyzeFilesWithSpinner("Analyzing baseline", baselineFiles)
        val currentMetrics = analyzeFilesWithSpinner("Analyzing current", currentFiles)

        println()
        println("═══════════════════════════════════════════════")
        println("  Run Group Statistics".asBold())
        println("═══════════════════════════════════════════════")
        println()

        // Display statistics
        displayGroupStatistics("BASELINE", baselineFiles, baselineMetrics)
        displayGroupStatistics("CURRENT", currentFiles, currentMetrics)

        // Calculate and display comparison
        displayComparison(baselineMetrics, currentMetrics, packageFilter)
    }

    private fun printHeader() {
        println("╔════════════════════════════════════════════╗")
        println("║  Performance Comparison Tool               ║")
        println("╚════════════════════════════════════════════╝")
        println()
    }

    /**
     * Extract base test name by removing run number suffixes. Examples:
     * - "test_run1.jfr" -> "test"
     * - "test_run_1.jfr" -> "test"
     * - "test.run2.jfr" -> "test"
     * - "test-run3.jfr" -> "test"
     * - "test_001.jfr" -> "test"
     * - "test.jfr" -> "test"
     */
    internal fun extractBaseTestName(filename: String): String {
        val nameWithoutExtension = filename.removeSuffix(".jfr")

        // Pattern to match various run number suffixes
        val patterns =
            listOf(
                """[_.-]run[_.-]\d+$""".toRegex(), // _run_1, .run.2, -run-3
                """[_.-]run\d+$""".toRegex(), // _run1, .run2, -run3
                """[_.-]\d+$""".toRegex(), // _1, .2, -3
            )

        var baseName = nameWithoutExtension
        for (pattern in patterns) {
            baseName = baseName.replace(pattern, "")
        }

        return baseName
    }

    internal fun getJfrFiles(path: File): List<File> =
        when {
            path.isFile && path.extension == "jfr" -> listOf(path)

            path.isDirectory ->
                path.walkTopDown().filter { it.isFile && it.extension == "jfr" }.sortedBy { it.name }.toList()

            else -> emptyList()
        }

    private suspend fun analyzeFilesWithSpinner(message: String, files: List<File>): List<Metrics> {
        val terminal = Terminal()
        val spinnerChars = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

        return coroutineScope {
            val metrics = mutableListOf<Metrics>()

            // Start the spinner
            val animationJob = launch {
                var frame = 0
                while (true) {
                    val spinnerChar = spinnerChars[frame % spinnerChars.size]
                    terminal.cursor.move {
                        startOfLine()
                        clearLineAfterCursor()
                    }
                    terminal.print("   $spinnerChar $message...")
                    frame++
                    delay(80.milliseconds)
                }
            }

            // Process files
            for (file in files) {
                metrics.add(extractMetrics(file))
            }

            // Stop the spinner
            animationJob.cancel()
            terminal.cursor.move {
                startOfLine()
                clearLineAfterCursor()
            }
            println("   ✓ $message completed")

            metrics
        }
    }

    private suspend fun extractMetrics(file: File): Metrics {
        val duration = extractDuration(file)
        val totalSamples = extractTotalSamples(file)
        val allocations = extractAllocations(file)
        val topMethods = extractTopMethods(file)
        val gcStats = extractGarbageCollectionStats(file)
        val cpuLoad = extractCpuLoadStats(file)
        val heapSummary = extractHeapSummaryStats(file)
        val metaspaceSummary = extractMetaspaceSummaryStats(file)

        return Metrics(
            duration = duration,
            totalSamples = totalSamples,
            allocations = allocations,
            topMethods = topMethods,
            gcStats = gcStats,
            cpuLoad = cpuLoad,
            heapSummary = heapSummary,
            metaspaceSummary = metaspaceSummary,
        )
    }

    internal suspend fun extractTopMethods(file: File): Map<String, Int> {
        val result =
            commandRunner(
                command = "jfr print --events jdk.ExecutionSample ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        if (!result.isSuccess) return emptyMap()

        val methodCounts = mutableMapOf<String, Int>()

        // JFR output format contains stack traces with method signatures
        // We need to extract package.Class.method from lines
        // Pattern matches: org.jetbrains.jewel.foundation.lazy.table.ClassName.methodName
        val methodPattern = """([a-zA-Z0-9_.]+\.[a-zA-Z0-9_]+\.[a-zA-Z0-9_]+)\(""".toRegex()

        result.output.lines().forEach { line ->
            methodPattern.findAll(line).forEach { match ->
                val fullMethod = match.groupValues[1]

                // Filter out JVM internal methods and common framework methods
                if (
                    !fullMethod.startsWith("java.") &&
                        !fullMethod.startsWith("jdk.") &&
                        !fullMethod.startsWith("sun.") &&
                        !fullMethod.startsWith("kotlin.coroutines.") &&
                        !fullMethod.startsWith("kotlinx.coroutines.")
                ) {
                    methodCounts[fullMethod] = methodCounts.getOrDefault(fullMethod, 0) + 1
                }
            }
        }

        // Return all methods, not just top N
        return methodCounts
    }

    internal suspend fun extractDuration(file: File): Long {
        val result =
            commandRunner(
                command = "jfr print ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        if (!result.isSuccess) return 0L

        val lines = result.output.lines()
        val timePattern = """startTime""".toRegex()
        val timestamps =
            lines
                .filter { timePattern.containsMatchIn(it) }
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 3) parts[2] else null
                }

        if (timestamps.size < 2) return 0L

        val first = parseTimestamp(timestamps.first())
        val last = parseTimestamp(timestamps.last())
        return last - first
    }

    internal fun parseTimestamp(timestamp: String): Long {
        val parts = timestamp.split(":")
        if (parts.size != 3) return 0L

        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secondsParts = parts[2].split(".")
        val seconds = secondsParts[0].toLongOrNull() ?: 0L
        val millis = secondsParts.getOrNull(1)?.toLongOrNull() ?: 0L

        return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
    }

    internal suspend fun extractTotalSamples(file: File): Int {
        val result =
            commandRunner(
                command = "jfr print --events jdk.ExecutionSample ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        return if (result.isSuccess) result.output.lines().size else 0
    }

    internal suspend fun extractAllocations(file: File): Int {
        val result =
            commandRunner(
                command = "jfr print --events jdk.ObjectAllocationSample ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        return if (result.isSuccess) result.output.lines().size else 0
    }

    internal suspend fun extractGarbageCollectionStats(file: File): GarbageCollectionStats {
        val result =
            commandRunner(
                command = "jfr print --json --events jdk.GarbageCollection ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        if (!result.isSuccess || result.output.isBlank()) {
            return GarbageCollectionStats()
        }

        return try {
            val jsonOutput = Json.parseToJsonElement(result.output).jsonObject
            val recording = jsonOutput["recording"]?.jsonObject ?: return GarbageCollectionStats()
            val events = recording["events"]?.jsonArray ?: return GarbageCollectionStats()

            val gcEvents =
                events.mapNotNull { event ->
                    val eventObj = event.jsonObject
                    val values = eventObj["values"]?.jsonObject ?: return@mapNotNull null
                    // Duration format is ISO 8601: "PT0.003685459S"
                    val durationStr = values["duration"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    parseDurationToMillis(durationStr)
                }

            if (gcEvents.isEmpty()) {
                GarbageCollectionStats()
            } else {
                GarbageCollectionStats(
                    totalGcCount = gcEvents.size,
                    totalGcPauseTimeMs = gcEvents.sum(),
                    avgGcPauseTimeMs = gcEvents.average(),
                    maxGcPauseTimeMs = gcEvents.maxOrNull() ?: 0L,
                )
            }
        } catch (_: Exception) {
            GarbageCollectionStats()
        }
    }

    /** Parse ISO 8601 duration format (e.g., "PT0.003685459S") to milliseconds */
    internal fun parseDurationToMillis(duration: String): Long? {
        return try {
            // Format: PT{seconds}S
            val secondsStr = duration.removePrefix("PT").removeSuffix("S")
            val seconds = secondsStr.toDoubleOrNull() ?: return null
            (seconds * 1000).toLong()
        } catch (_: Exception) {
            null
        }
    }

    internal suspend fun extractCpuLoadStats(file: File): CpuLoadStats {
        val result =
            commandRunner(
                command = "jfr print --json --events jdk.CPULoad ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        if (!result.isSuccess || result.output.isBlank()) {
            return CpuLoadStats()
        }

        return try {
            val jsonOutput = Json.parseToJsonElement(result.output).jsonObject
            val recording = jsonOutput["recording"]?.jsonObject ?: return CpuLoadStats()
            val events = recording["events"]?.jsonArray ?: return CpuLoadStats()

            val jvmCpuValues = mutableListOf<Double>()
            val machineCpuValues = mutableListOf<Double>()

            events.forEach { event ->
                val eventObj = event.jsonObject
                val values = eventObj["values"]?.jsonObject ?: return@forEach

                values["jvmUser"]?.jsonPrimitive?.double?.let { jvmUser ->
                    values["jvmSystem"]?.jsonPrimitive?.double?.let { jvmSystem ->
                        jvmCpuValues.add(jvmUser + jvmSystem)
                    }
                }

                values["machineTotal"]?.jsonPrimitive?.double?.let { machineTotal ->
                    machineCpuValues.add(machineTotal)
                }
            }

            if (jvmCpuValues.isEmpty() && machineCpuValues.isEmpty()) {
                CpuLoadStats()
            } else {
                CpuLoadStats(
                    avgJvmCpu = if (jvmCpuValues.isNotEmpty()) jvmCpuValues.average() * 100 else 0.0,
                    avgMachineCpu = if (machineCpuValues.isNotEmpty()) machineCpuValues.average() * 100 else 0.0,
                    maxJvmCpu = (jvmCpuValues.maxOrNull() ?: 0.0) * 100,
                    maxMachineCpu = (machineCpuValues.maxOrNull() ?: 0.0) * 100,
                )
            }
        } catch (_: Exception) {
            CpuLoadStats()
        }
    }

    internal suspend fun extractHeapSummaryStats(file: File): HeapSummaryStats {
        val result =
            commandRunner(
                command = "jfr print --json --events jdk.GCHeapSummary ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        if (!result.isSuccess || result.output.isBlank()) {
            return HeapSummaryStats()
        }

        return try {
            val jsonOutput = Json.parseToJsonElement(result.output).jsonObject
            val recording = jsonOutput["recording"]?.jsonObject ?: return HeapSummaryStats()
            val events = recording["events"]?.jsonArray ?: return HeapSummaryStats()

            val heapUsedValues = mutableListOf<Long>()
            val committedValues = mutableListOf<Long>()

            events.forEach { event ->
                val eventObj = event.jsonObject
                val values = eventObj["values"]?.jsonObject ?: return@forEach

                values["heapUsed"]?.jsonPrimitive?.longOrNull?.let { heapUsed ->
                    heapUsedValues.add(heapUsed / (1024 * 1024)) // Convert to MB
                }

                values["committedSize"]?.jsonPrimitive?.longOrNull?.let { committed ->
                    committedValues.add(committed / (1024 * 1024)) // Convert to MB
                }
            }

            if (heapUsedValues.isEmpty()) {
                HeapSummaryStats()
            } else {
                HeapSummaryStats(
                    avgHeapUsedMb = heapUsedValues.average(),
                    maxHeapUsedMb = heapUsedValues.maxOrNull() ?: 0L,
                    avgCommittedSizeMb = if (committedValues.isNotEmpty()) committedValues.average() else 0.0,
                )
            }
        } catch (_: Exception) {
            HeapSummaryStats()
        }
    }

    internal suspend fun extractMetaspaceSummaryStats(file: File): MetaspaceSummaryStats {
        val result =
            commandRunner(
                command = "jfr print --json --events jdk.MetaspaceSummary ${file.absolutePath}",
                workingDir = null,
                exitOnError = false,
                timeoutAmount = 30.seconds,
            )

        if (!result.isSuccess || result.output.isBlank()) {
            return MetaspaceSummaryStats()
        }

        return try {
            val jsonOutput = Json.parseToJsonElement(result.output).jsonObject
            val recording = jsonOutput["recording"]?.jsonObject ?: return MetaspaceSummaryStats()
            val events = recording["events"]?.jsonArray ?: return MetaspaceSummaryStats()

            val metaspaceUsedValues = mutableListOf<Long>()
            val metaspaceCommittedValues = mutableListOf<Long>()

            events.forEach { event ->
                val eventObj = event.jsonObject
                val values = eventObj["values"]?.jsonObject ?: return@forEach

                // Metaspace data is typically in the "metaspace" object
                values["metaspace"]?.jsonObject?.let { metaspaceObj ->
                    metaspaceObj["used"]?.jsonPrimitive?.longOrNull?.let { used ->
                        metaspaceUsedValues.add(used / (1024 * 1024)) // Convert to MB
                    }

                    metaspaceObj["committed"]?.jsonPrimitive?.longOrNull?.let { committed ->
                        metaspaceCommittedValues.add(committed / (1024 * 1024)) // Convert to MB
                    }
                }
            }

            if (metaspaceUsedValues.isEmpty()) {
                MetaspaceSummaryStats()
            } else {
                MetaspaceSummaryStats(
                    avgMetaspaceUsedMb = metaspaceUsedValues.average(),
                    maxMetaspaceUsedMb = metaspaceUsedValues.maxOrNull() ?: 0L,
                    avgMetaspaceCommittedMb =
                        if (metaspaceCommittedValues.isNotEmpty()) {
                            metaspaceCommittedValues.average()
                        } else {
                            0.0
                        },
                )
            }
        } catch (_: Exception) {
            MetaspaceSummaryStats()
        }
    }

    private fun displayGroupStatistics(name: String, files: List<File>, metrics: List<Metrics>) {
        if (files.size == 1) {
            println("$name: ".asWarning() + "Single run (no statistics needed)")
            println()
            return
        }

        println("$name Statistics: ".asWarning() + "${files.size} runs")
        println()

        displayMetricStats("Duration (ms)", metrics.map { it.duration })
        displayMetricStats("Total CPU Samples", metrics.map { it.totalSamples.toLong() })
        displayMetricStats("Object Allocations", metrics.map { it.allocations.toLong() })

        // GC Statistics
        displayMetricStats("GC Count", metrics.map { it.gcStats.totalGcCount.toLong() })
        displayMetricStats("Total GC Pause (ms)", metrics.map { it.gcStats.totalGcPauseTimeMs })
        displayMetricStats("Max GC Pause (ms)", metrics.map { it.gcStats.maxGcPauseTimeMs })

        // Heap Statistics
        displayMetricStats("Max Heap Used (MB)", metrics.map { it.heapSummary.maxHeapUsedMb })

        // Metaspace Statistics
        displayMetricStats("Max Metaspace Used (MB)", metrics.map { it.metaspaceSummary.maxMetaspaceUsedMb })

        println()

        // Show top methods across all runs
        val aggregatedMethods =
            metrics
                .flatMap { it.topMethods.entries }
                .groupBy { it.key }
                .mapValues { (_, entries) -> entries.sumOf { it.value } }
                .entries
                .sortedByDescending { it.value }
                .take(10)

        println("Top Methods:".asWarning())
        aggregatedMethods.forEach { (method, count) -> println("  %6d  %s".format(count, method)) }
        println()
    }

    private fun displayMetricStats(name: String, values: List<Long>) {
        val stats = calculateStatistics(values)
        println(name.asWarning() + ":")
        println(
            "  Mean: ${stats.mean}, Median: ${stats.median}, Mode: ${stats.mode}, " +
                "StdDev: ${"%.2f".format(stats.stdDev)}, Min: ${stats.min}, Max: ${stats.max}"
        )
    }

    internal fun calculateStatistics(values: List<Long>): Statistics {
        val sorted = values.sorted()
        val mean = values.average().toLong()
        val median =
            if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            } else {
                sorted[sorted.size / 2]
            }

        val mode = values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: mean

        val stdDev =
            if (values.size > 1) {
                val variance = values.map { (it - mean).toDouble().let { diff -> diff * diff } }.average()
                sqrt(variance)
            } else {
                0.0
            }

        return Statistics(
            mean = mean,
            median = median,
            mode = mode,
            stdDev = stdDev,
            min = sorted.first(),
            max = sorted.last(),
        )
    }

    private fun displayComparison(baseline: List<Metrics>, current: List<Metrics>, packageFilter: String) {
        val baselineAvg = calculateAverageMetrics(baseline)
        val currentAvg = calculateAverageMetrics(current)

        println()
        println("═══════════════════════════════════════════════")
        println("  Comparison Results".asBold())
        println("═══════════════════════════════════════════════")
        println()

        // For absolute metrics like duration and allocations, still use absolute comparison
        val durationChange = calculateChange(baselineAvg.duration, currentAvg.duration)
        val totalSamplesChange = calculateChange(baselineAvg.totalSamples.toLong(), currentAvg.totalSamples.toLong())
        val allocationsChange = calculateChange(baselineAvg.allocations.toLong(), currentAvg.allocations.toLong())

        printMetric("Duration (milliseconds)", baselineAvg.duration, currentAvg.duration, durationChange, true)
        printMetric(
            "Total CPU Samples",
            baselineAvg.totalSamples.toLong(),
            currentAvg.totalSamples.toLong(),
            totalSamplesChange,
            true,
        )
        printMetric(
            "Object Allocations",
            baselineAvg.allocations.toLong(),
            currentAvg.allocations.toLong(),
            allocationsChange,
            true,
        )

        println()
        println("--- Garbage Collection Metrics ---")
        val gcCountChange =
            calculateChange(baselineAvg.gcStats.totalGcCount.toLong(), currentAvg.gcStats.totalGcCount.toLong())
        val gcPauseTimeChange =
            calculateChange(baselineAvg.gcStats.totalGcPauseTimeMs, currentAvg.gcStats.totalGcPauseTimeMs)
        val maxGcPauseChange =
            calculateChange(baselineAvg.gcStats.maxGcPauseTimeMs, currentAvg.gcStats.maxGcPauseTimeMs)

        printMetric(
            "GC Count",
            baselineAvg.gcStats.totalGcCount.toLong(),
            currentAvg.gcStats.totalGcCount.toLong(),
            gcCountChange,
            true,
        )
        printMetric(
            "Total GC Pause Time (ms)",
            baselineAvg.gcStats.totalGcPauseTimeMs,
            currentAvg.gcStats.totalGcPauseTimeMs,
            gcPauseTimeChange,
            true,
        )
        printMetric(
            "Avg GC Pause Time (ms)",
            baselineAvg.gcStats.avgGcPauseTimeMs.toLong(),
            currentAvg.gcStats.avgGcPauseTimeMs.toLong(),
            calculateChange(
                baselineAvg.gcStats.avgGcPauseTimeMs.toLong(),
                currentAvg.gcStats.avgGcPauseTimeMs.toLong(),
            ),
            true,
        )
        printMetric(
            "Max GC Pause Time (ms)",
            baselineAvg.gcStats.maxGcPauseTimeMs,
            currentAvg.gcStats.maxGcPauseTimeMs,
            maxGcPauseChange,
            true,
        )

        println()
        println("--- CPU Load Metrics ---")
        val avgJvmCpuChange =
            calculateChange(
                (baselineAvg.cpuLoad.avgJvmCpu * 100).toLong(),
                (currentAvg.cpuLoad.avgJvmCpu * 100).toLong(),
            )
        val avgMachineCpuChange =
            calculateChange(
                (baselineAvg.cpuLoad.avgMachineCpu * 100).toLong(),
                (currentAvg.cpuLoad.avgMachineCpu * 100).toLong(),
            )

        printMetric(
            "Avg JVM CPU (%)",
            (baselineAvg.cpuLoad.avgJvmCpu * 100).toLong(),
            (currentAvg.cpuLoad.avgJvmCpu * 100).toLong(),
            avgJvmCpuChange,
            true,
        )
        printMetric(
            "Max JVM CPU (%)",
            (baselineAvg.cpuLoad.maxJvmCpu * 100).toLong(),
            (currentAvg.cpuLoad.maxJvmCpu * 100).toLong(),
            calculateChange(
                (baselineAvg.cpuLoad.maxJvmCpu * 100).toLong(),
                (currentAvg.cpuLoad.maxJvmCpu * 100).toLong(),
            ),
            true,
        )
        printMetric(
            "Avg Machine CPU (%)",
            (baselineAvg.cpuLoad.avgMachineCpu * 100).toLong(),
            (currentAvg.cpuLoad.avgMachineCpu * 100).toLong(),
            avgMachineCpuChange,
            true,
        )
        printMetric(
            "Max Machine CPU (%)",
            (baselineAvg.cpuLoad.maxMachineCpu * 100).toLong(),
            (currentAvg.cpuLoad.maxMachineCpu * 100).toLong(),
            calculateChange(
                (baselineAvg.cpuLoad.maxMachineCpu * 100).toLong(),
                (currentAvg.cpuLoad.maxMachineCpu * 100).toLong(),
            ),
            true,
        )

        println()
        println("--- Heap Memory Metrics ---")
        val avgHeapChange =
            calculateChange(
                baselineAvg.heapSummary.avgHeapUsedMb.toLong(),
                currentAvg.heapSummary.avgHeapUsedMb.toLong(),
            )
        val maxHeapChange = calculateChange(baselineAvg.heapSummary.maxHeapUsedMb, currentAvg.heapSummary.maxHeapUsedMb)

        printMetric(
            "Avg Heap Used (MB)",
            baselineAvg.heapSummary.avgHeapUsedMb.toLong(),
            currentAvg.heapSummary.avgHeapUsedMb.toLong(),
            avgHeapChange,
            true,
        )
        printMetric(
            "Max Heap Used (MB)",
            baselineAvg.heapSummary.maxHeapUsedMb,
            currentAvg.heapSummary.maxHeapUsedMb,
            maxHeapChange,
            true,
        )
        printMetric(
            "Avg Committed Size (MB)",
            baselineAvg.heapSummary.avgCommittedSizeMb.toLong(),
            currentAvg.heapSummary.avgCommittedSizeMb.toLong(),
            calculateChange(
                baselineAvg.heapSummary.avgCommittedSizeMb.toLong(),
                currentAvg.heapSummary.avgCommittedSizeMb.toLong(),
            ),
            true,
        )

        println()
        println("--- Metaspace Memory Metrics ---")
        val avgMetaspaceChange =
            calculateChange(
                baselineAvg.metaspaceSummary.avgMetaspaceUsedMb.toLong(),
                currentAvg.metaspaceSummary.avgMetaspaceUsedMb.toLong(),
            )
        val maxMetaspaceChange =
            calculateChange(
                baselineAvg.metaspaceSummary.maxMetaspaceUsedMb,
                currentAvg.metaspaceSummary.maxMetaspaceUsedMb,
            )

        printMetric(
            "Avg Metaspace Used (MB)",
            baselineAvg.metaspaceSummary.avgMetaspaceUsedMb.toLong(),
            currentAvg.metaspaceSummary.avgMetaspaceUsedMb.toLong(),
            avgMetaspaceChange,
            true,
        )
        printMetric(
            "Max Metaspace Used (MB)",
            baselineAvg.metaspaceSummary.maxMetaspaceUsedMb,
            currentAvg.metaspaceSummary.maxMetaspaceUsedMb,
            maxMetaspaceChange,
            true,
        )
        printMetric(
            "Avg Metaspace Committed (MB)",
            baselineAvg.metaspaceSummary.avgMetaspaceCommittedMb.toLong(),
            currentAvg.metaspaceSummary.avgMetaspaceCommittedMb.toLong(),
            calculateChange(
                baselineAvg.metaspaceSummary.avgMetaspaceCommittedMb.toLong(),
                currentAvg.metaspaceSummary.avgMetaspaceCommittedMb.toLong(),
            ),
            true,
        )

        println()
        println("═══════════════════════════════════════════════")
        println()

        // Method-level comparison - identify main aggressors
        println("📊 Performance Hotspot Analysis:".asWarning())
        println()

        val baselineMethods =
            baseline
                .flatMap { it.topMethods.entries }
                .groupBy { it.key }
                .mapValues { (_, entries) -> entries.sumOf { it.value } }

        val currentMethods =
            current
                .flatMap { it.topMethods.entries }
                .groupBy { it.key }
                .mapValues { (_, entries) -> entries.sumOf { it.value } }

        val baselineTotal = baselineMethods.values.sum()
        val currentTotal = currentMethods.values.sum()

        // Filter to focus on specified package
        val currentFilteredMethods = currentMethods.filterKeys { it.startsWith(packageFilter) }
        val baselineFilteredMethods = baselineMethods.filterKeys { it.startsWith(packageFilter) }

        // Show all methods summary first
        println("Overall CPU Sample Distribution:".asWarning())
        println("  Total samples (baseline): $baselineTotal")
        println("  Total samples (current):  $currentTotal")
        println(
            "  $packageFilter samples (baseline): ${baselineFilteredMethods.values.sum()} (${
                if (baselineTotal > 0) {
                    "%.1f".format(
                        (baselineFilteredMethods.values.sum() * 100.0) / baselineTotal
                    )
                } else {
                    "0.0"
                }
            }%)"
        )
        println(
            "  $packageFilter samples (current):  ${currentFilteredMethods.values.sum()} (${
                if (currentTotal > 0) {
                    "%.1f".format(
                        (currentFilteredMethods.values.sum() * 100.0) / currentTotal
                    )
                } else {
                    "0.0"
                }
            }%)"
        )
        println()

        // Identify top aggressors in current run (methods consuming most CPU)
        println("Top $packageFilter CPU Consumers:".asError())
        println()

        val topAggressors = currentFilteredMethods.entries.sortedByDescending { it.value }.take(20)

        if (topAggressors.isEmpty()) {
            println("  No $packageFilter methods found in profiling data".asWarning())
            println()

            // Show what methods were actually found for debugging
            val sampleMethods = currentMethods.entries.sortedByDescending { it.value }.take(10)

            if (sampleMethods.isNotEmpty()) {
                println("  Sample of methods found in current run:".asWarning())
                sampleMethods.forEach { (method, count) -> println("    %6d samples: %s".format(count, method)) }
                println()
            }
        } else {
            topAggressors.forEach { (method, currentCount) ->
                val baselineCount = baselineMethods[method] ?: 0
                val currentPercent = if (currentTotal > 0) (currentCount * 100.0) / currentTotal else 0.0
                val baselinePercent =
                    if (baselineTotal > 0 && baselineCount > 0) {
                        (baselineCount * 100.0) / baselineTotal
                    } else {
                        0.0
                    }

                // Calculate change based on absolute counts
                val change =
                    if (baselineCount > 0) {
                        ((currentCount - baselineCount) * 100.0) / baselineCount
                    } else if (currentCount > 0) {
                        Double.POSITIVE_INFINITY // New method
                    } else {
                        null
                    }

                val percentDiff = currentPercent - baselinePercent

                val changeStr =
                    if (change != null) {
                        val sign = if (change > 0) "+" else ""
                        val color =
                            when {
                                change > 10.0 -> "$sign${"%.1f".format(change)}%".asError()
                                change < -10.0 -> "$sign${"%.1f".format(change)}%".asSuccess()
                                else -> "$sign${"%.1f".format(change)}%".asWarning()
                            }
                        color
                    } else {
                        "NEW".asError()
                    }

                val percentDiffStr =
                    if (percentDiff > 0) {
                        "+${"%.1f".format(percentDiff)}pp".asError()
                    } else if (percentDiff < 0) {
                        "${"%.1f".format(percentDiff)}pp".asSuccess()
                    } else {
                        " ${"%.1f".format(percentDiff)}pp".asWarning()
                    }

                println(
                    "  %6d samples (%5.1f%%) [baseline: %6d (%5.1f%%)] %12s %10s\n    %s"
                        .format(
                            currentCount,
                            currentPercent,
                            baselineCount,
                            baselinePercent,
                            changeStr,
                            percentDiffStr,
                            method,
                        )
                )
            }
            println()
        }

        // Also show top non-filtered methods for context
        println("Top Non-$packageFilter CPU Consumers (for context):".asWarning())
        println()

        val topNonFiltered =
            currentMethods.entries.filter { !it.key.startsWith(packageFilter) }.sortedByDescending { it.value }.take(10)

        topNonFiltered.forEach { (method, currentCount) ->
            val currentPercent = if (currentTotal > 0) (currentCount * 100.0) / currentTotal else 0.0
            println("  %6d samples (%5.1f%%) %s".format(currentCount, currentPercent, method))
        }
        println()

        // Find filtered methods with significant changes (both increases and decreases)
        val allFilteredMethods = (baselineFilteredMethods.keys + currentFilteredMethods.keys).distinct()
        val significantChanges =
            allFilteredMethods
                .mapNotNull { method ->
                    val baselineCount = baselineMethods[method] ?: 0
                    val currentCount = currentMethods[method] ?: 0

                    val currentPercent = if (currentTotal > 0) (currentCount * 100.0) / currentTotal else 0.0
                    val baselinePercent =
                        if (baselineTotal > 0 && baselineCount > 0) {
                            (baselineCount * 100.0) / baselineTotal
                        } else {
                            0.0
                        }

                    // Calculate change based on absolute counts
                    val change =
                        if (baselineCount > 0) {
                            ((currentCount - baselineCount) * 100.0) / baselineCount
                        } else if (currentCount > 0) {
                            Double.POSITIVE_INFINITY // New method
                        } else {
                            null
                        }

                    // Threshold of 20% change in absolute counts to catch significant changes
                    if (change != null && (kotlin.math.abs(change) >= 20.0 || change == Double.POSITIVE_INFINITY)) {
                        MethodChange(method, baselineCount, currentCount, change, currentPercent, baselinePercent)
                    } else {
                        null
                    }
                }
                .sortedByDescending { it.current }

        if (significantChanges.isNotEmpty()) {
            println("Significant $packageFilter Method Changes (threshold: ±20%):".asWarning())
            println()

            val regressions = significantChanges.filter { it.change > 0 }.take(10)
            val improvements = significantChanges.filter { it.change < 0 }.take(10)

            if (regressions.isNotEmpty()) {
                println("Major Regressions:".asError())
                regressions.forEach { change ->
                    val changeStr =
                        if (change.change == Double.POSITIVE_INFINITY) {
                            "NEW".asError()
                        } else {
                            val sign = "+"
                            "$sign${"%.0f".format(change.change)}%".asError()
                        }

                    val percentDiff = change.currentPercent - change.baselinePercent
                    val percentDiffStr =
                        if (percentDiff > 0) {
                            "+${"%.1f".format(percentDiff)}pp"
                        } else {
                            "${"%.1f".format(percentDiff)}pp"
                        }

                    println(
                        "  %6d → %6d (%s, %s)\n    %s"
                            .format(change.baseline, change.current, changeStr, percentDiffStr, change.method)
                    )
                }
                println()
            }

            if (improvements.isNotEmpty()) {
                println("Major Improvements:".asSuccess())
                improvements.forEach { change ->
                    val sign = ""
                    val changeStr = "$sign${"%.0f".format(change.change)}%".asSuccess()

                    val percentDiff = change.currentPercent - change.baselinePercent
                    val percentDiffStr =
                        if (percentDiff > 0) {
                            "+${"%.1f".format(percentDiff)}pp"
                        } else {
                            "${"%.1f".format(percentDiff)}pp"
                        }

                    println(
                        "  %6d → %6d (%s, %s)\n    %s"
                            .format(change.baseline, change.current, changeStr, percentDiffStr, change.method)
                    )
                }
                println()
            }
        }

        // Overall assessment
        println("Overall Assessment:".asWarning())
        println()

        var improvements = 0
        var regressions = 0

        listOf(totalSamplesChange, allocationsChange).forEach { change ->
            if (change != null) {
                when {
                    change < -10.0 -> improvements++
                    change > 10.0 -> regressions++
                }
            }
        }

        // Add method-level changes to assessment. `change > 20.0` already covers the POSITIVE_INFINITY
        // "new method" case (infinity compares greater than any finite double), so no separate check is needed.
        significantChanges.forEach { change ->
            when {
                change.change < -20.0 -> improvements++
                change.change > 20.0 -> regressions++
            }
        }

        println("Improvements: $improvements")
        println("Regressions:  $regressions")
        println()

        when {
            improvements > regressions -> println("✅ Performance IMPROVED overall".asSuccess())
            regressions > improvements -> println("⚠️  Performance REGRESSED overall".asError())
            else -> println("➡️  Performance unchanged or mixed results".asWarning())
        }
    }

    private fun calculateAverageMetrics(metrics: List<Metrics>): Metrics {
        val aggregatedMethods =
            metrics
                .flatMap { it.topMethods.entries }
                .groupBy { it.key }
                .mapValues { (_, entries) -> entries.sumOf { it.value } / metrics.size }

        // Average GC stats
        val avgGcStats =
            GarbageCollectionStats(
                totalGcCount = metrics.map { it.gcStats.totalGcCount }.average().toInt(),
                totalGcPauseTimeMs = metrics.map { it.gcStats.totalGcPauseTimeMs }.average().toLong(),
                avgGcPauseTimeMs = metrics.map { it.gcStats.avgGcPauseTimeMs }.average(),
                maxGcPauseTimeMs = metrics.map { it.gcStats.maxGcPauseTimeMs }.maxOrNull() ?: 0L,
            )

        // Average CPU stats
        val avgCpuLoad =
            CpuLoadStats(
                avgJvmCpu = metrics.map { it.cpuLoad.avgJvmCpu }.average(),
                avgMachineCpu = metrics.map { it.cpuLoad.avgMachineCpu }.average(),
                maxJvmCpu = metrics.map { it.cpuLoad.maxJvmCpu }.maxOrNull() ?: 0.0,
                maxMachineCpu = metrics.map { it.cpuLoad.maxMachineCpu }.maxOrNull() ?: 0.0,
            )

        // Average Heap stats
        val avgHeapSummary =
            HeapSummaryStats(
                avgHeapUsedMb = metrics.map { it.heapSummary.avgHeapUsedMb }.average(),
                maxHeapUsedMb = metrics.map { it.heapSummary.maxHeapUsedMb }.maxOrNull() ?: 0L,
                avgCommittedSizeMb = metrics.map { it.heapSummary.avgCommittedSizeMb }.average(),
            )

        // Average Metaspace stats
        val avgMetaspaceSummary =
            MetaspaceSummaryStats(
                avgMetaspaceUsedMb = metrics.map { it.metaspaceSummary.avgMetaspaceUsedMb }.average(),
                maxMetaspaceUsedMb = metrics.map { it.metaspaceSummary.maxMetaspaceUsedMb }.maxOrNull() ?: 0L,
                avgMetaspaceCommittedMb = metrics.map { it.metaspaceSummary.avgMetaspaceCommittedMb }.average(),
            )

        return Metrics(
            duration = metrics.map { it.duration }.average().toLong(),
            totalSamples = metrics.map { it.totalSamples }.average().toInt(),
            allocations = metrics.map { it.allocations }.average().toInt(),
            topMethods = aggregatedMethods,
            gcStats = avgGcStats,
            cpuLoad = avgCpuLoad,
            heapSummary = avgHeapSummary,
            metaspaceSummary = avgMetaspaceSummary,
        )
    }

    internal fun calculateChange(baseline: Long, current: Long): Double? {
        if (baseline == 0L) return null
        return ((current - baseline) * 100.0) / baseline
    }

    private fun printMetric(name: String, baseline: Long, current: Long, change: Double?, invert: Boolean) {
        val changeStr =
            if (change == null) {
                "N/A".asWarning()
            } else {
                val sign = if (change > 0) "+" else ""
                val formatted = "$sign${"%.1f".format(change)}%"
                val color =
                    when {
                        invert && change < -10.0 -> formatted.asSuccess()
                        invert && change > 10.0 -> formatted.asError()
                        !invert && change > 10.0 -> formatted.asSuccess()
                        !invert && change < -10.0 -> formatted.asError()
                        else -> formatted.asWarning()
                    }
                "($color)"
            }

        println("%-30s %10s → %-10s %s".format(name, baseline, current, changeStr))
    }

    data class Metrics(
        val duration: Long,
        val totalSamples: Int,
        val allocations: Int,
        val topMethods: Map<String, Int>,
        val gcStats: GarbageCollectionStats = GarbageCollectionStats(),
        val cpuLoad: CpuLoadStats = CpuLoadStats(),
        val heapSummary: HeapSummaryStats = HeapSummaryStats(),
        val metaspaceSummary: MetaspaceSummaryStats = MetaspaceSummaryStats(),
    )

    data class GarbageCollectionStats(
        val totalGcCount: Int = 0,
        val totalGcPauseTimeMs: Long = 0,
        val avgGcPauseTimeMs: Double = 0.0,
        val maxGcPauseTimeMs: Long = 0,
    )

    data class CpuLoadStats(
        val avgJvmCpu: Double = 0.0,
        val avgMachineCpu: Double = 0.0,
        val maxJvmCpu: Double = 0.0,
        val maxMachineCpu: Double = 0.0,
    )

    data class HeapSummaryStats(
        val avgHeapUsedMb: Double = 0.0,
        val maxHeapUsedMb: Long = 0,
        val avgCommittedSizeMb: Double = 0.0,
    )

    data class MetaspaceSummaryStats(
        val avgMetaspaceUsedMb: Double = 0.0,
        val maxMetaspaceUsedMb: Long = 0,
        val avgMetaspaceCommittedMb: Double = 0.0,
    )

    data class MethodChange(
        val method: String,
        val baseline: Int,
        val current: Int,
        val change: Double,
        val currentPercent: Double,
        val baselinePercent: Double,
    )

    data class Statistics(
        val mean: Long,
        val median: Long,
        val mode: Long,
        val stdDev: Double,
        val min: Long,
        val max: Long,
    )

    data class TestComparisonResult(
        val testName: String,
        val improvements: Int,
        val regressions: Int,
        val durationChange: Double?,
        val samplesChange: Double?,
        val allocationsChange: Double?,
    )
}
