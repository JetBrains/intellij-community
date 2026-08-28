@file:Suppress("RAW_RUN_BLOCKING", "IO_FILE_USAGE")

package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val UI_TESTS_TARGET = "//platform/jewel/ui-tests:ui-tests_test"

/**
 * Performance Test Runner
 * Automated test execution with JFR profiling and comparison
 *
 * Examples:
 * - Run tests with JFR profiling (10 runs, one JFR file per run):
 *   bazel run //platform/jewel/build-scripts/bazel:runPerformanceTests -- --jfr --runs 10
 *
 * - Run with custom heap size (useful to avoid OutOfMemoryError):
 *   bazel run //platform/jewel/build-scripts/bazel:runPerformanceTests -- --jfr --heap 8g
 */
fun main(args: Array<String>) {
    runBlocking { RunPerformanceTestsCommand().main(args) }
}

internal class RunPerformanceTestsCommand(private val commandRunner: CommandRunner = DefaultCommandRunner) :
    SuspendingCliktCommand(name = "run-performance-tests") {

    private val profileJfr: Boolean by option("--jfr", help = "Enable JFR profiling").flag(default = false)

    private val duration: Int by option("--duration", help = "JFR duration in seconds").int().default(60)

    private val testPattern: String by
        option("--test", help = "Test class pattern (e.g., TablePerformanceTest or *Performance*)")
            .default("*Performance*,*Benchmark*")

    private val outputName: String by option("--name", help = "Output folder name").default("")

    private val numRuns: Int by
        option("--runs", help = "Number of runs. This is only used if '--jfr' is set").int().default(3)

    // TODO: missing perTest option. We need to provide a "dry run" alternative on the Bazel side first.

    private val heapSize: String by option("--heap", help = "Maximum heap size for tests (e.g., 2g, 4g)").default("4g")

    override fun help(context: Context): String = "Run performance tests with optional JFR profiling"

    override suspend fun run() {
        printHeader()

        val communityRoot = getBuildWorkspaceDirectory()
        val resultsDir = File(communityRoot, "platform/jewel/analysis/profiling_results")
        resultsDir.mkdirs()

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val finalOutputName = outputName.ifBlank { "perf_test_$timestamp" }
        val outputDir = File(resultsDir, finalOutputName)

        if (profileJfr) {
            outputDir.mkdirs()
            println("📊 JFR Profiling enabled".asWarning())
            println("   Output directory: $outputDir")
            println("   Number of runs: $numRuns")
            println("   Duration per run: ${duration}s")
            println()
        }

        val totalRuns = if (profileJfr) numRuns else 1
        var testExitCode = 0

        println("🚀 Running performance tests...".asSuccess())
        println("   Test pattern: $testPattern")
        println("   Heap size: $heapSize")
        println()

        val classNameFilters = buildClassNameFilters(testPattern)

        // TODO: --per-test needs one JFR file per test method, which requires discovering tests without
        //  running them. JUnit5BazelRunner already does that discovery internally, it's just not exposed.
        for (run in 1..totalRuns) {
            val jvmOpts =
                if (profileJfr) {
                    buildJfrJvmOpts(heapSize, File(outputDir, "run_$run.jfr"), duration)
                } else {
                    buildHeapJvmOpts(heapSize)
                }

            val command = buildBazelTestCommand(UI_TESTS_TARGET, classNameFilters, jvmOpts)
            val result = runTestsWithSpinner(command, communityRoot, run, totalRuns)

            if (result.isFailure) {
                testExitCode = 1
                println("   - ❌ Run $run failed".asError())
                break
            } else {
                println("   - ✅ Run $run completed successfully!".asSuccess())
            }
        }

        if (testExitCode == 0) {
            println()
            println("   - ✅ All tests completed successfully!".asSuccess())
        } else {
            println("   - ❌ Tests failed".asError())
        }

        if (profileJfr && outputDir.exists()) {
            showJfrSummary(outputDir)
        }

        if (testExitCode != 0) {
            exitWithError("Tests failed")
        }
    }

    private suspend fun runTestsWithSpinner(command: String, communityRoot: File, run: Int, totalRuns: Int): CmdResult {
        val terminal = Terminal()
        val spinnerChars = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

        return coroutineScope {
            val animationJob = launch {
                var frame = 0
                while (true) {
                    val spinnerChar = spinnerChars[frame % spinnerChars.size]
                    terminal.cursor.move {
                        startOfLine()
                        clearLineAfterCursor()
                    }
                    terminal.print("   $spinnerChar Executing tests ($run/$totalRuns)...")
                    frame++
                    delay(80.milliseconds)
                }
            }

            val result =
                commandRunner(
                    command = command,
                    workingDir = communityRoot,
                    exitOnError = false,
                    timeoutAmount = 15.minutes,
                )

            animationJob.cancel()
            terminal.cursor.move {
                startOfLine()
                clearLineAfterCursor()
            }

            result
        }
    }

    private fun printHeader() {
        println("╔════════════════════════════════════════════╗")
        println("║  Performance Test Runner                   ║")
        println("╚════════════════════════════════════════════╝")
        println()
    }

    private fun showJfrSummary(outputDir: File) {
        println()
        println("╔════════════════════════════════════════════╗")
        println("║  JFR Profile Summary                       ║")
        println("╚════════════════════════════════════════════╝")
        println()
        println("📁 Output directory: $outputDir".asWarning())
        println()

        val jfrFiles = outputDir.listFiles { file -> file.extension == "jfr" }
        if (jfrFiles != null && jfrFiles.isNotEmpty()) {
            println("Generated JFR files:".asWarning())
            jfrFiles.sortedBy { it.name }.forEach { file ->
                val sizeKb = file.length() / 1024
                println("  ${file.name} (${sizeKb}KB)")
            }
        }

        println()
        println("═══════════════════════════════════════════════")
        println()
        println("Useful commands:".asWarning())
        println()
        println("  # Compare runs using the compare script")
        println("  bazel run //platform/jewel/build-scripts/bazel:comparePerformance -- $outputDir <other_folder>")
        println()
        println("  # View summary of a specific run")
        println("  jfr summary $outputDir/run_1.jfr")
        println()
        println("  # Execution samples from a specific run")
        println("  jfr print --events jdk.ExecutionSample $outputDir/run_1.jfr")
        println()
        println("  # Open in JDK Mission Control")
        println("  jmc -open $outputDir/run_1.jfr")
        println()
    }
}

/**
 * Converts a comma-separated list of `*`-glob patterns (Gradle's `--tests` syntax, e.g. `*Performance*,*Benchmark*`)
 * into the `JB_TEST_JUNIT5_FILTERS` format `JUnit5BazelRunner` reads from its environment.
 */
internal fun buildClassNameFilters(testPattern: String): String =
    testPattern
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(";") { "include-classname=${wildcardToClassNameRegex(it)}" }

/** Converts a single `*`-glob pattern into the regex `ClassNameFilter.includeClassNamePatterns` expects. */
internal fun wildcardToClassNameRegex(pattern: String): String =
    // Regex.escape("") returns "\Q\E", not "" — skip escaping empty segments (from a leading/trailing/
    // consecutive `*`) so they don't pollute the result with no-op \Q\E markers.
    pattern.split("*").joinToString(".*") { if (it.isEmpty()) "" else Regex.escape(it) }

internal fun buildHeapJvmOpts(heapSize: String): List<String> = listOf("-Xmx$heapSize", "-Xms$heapSize")

internal fun buildJfrJvmOpts(heapSize: String, jfrFile: File, durationSeconds: Int): List<String> =
    buildHeapJvmOpts(heapSize) +
        listOf(
            "-XX:StartFlightRecording=filename=${jfrFile.absolutePath},duration=${durationSeconds}s,settings=profile",
            "-XX:FlightRecorderOptions=stackdepth=256",
        )

internal fun buildBazelTestCommand(target: String, classNameFilters: String, jvmOpts: List<String>): String =
    buildString {
        append("./bazel.cmd test $target")
        append(" --test_env=JB_TEST_JUNIT5_FILTERS=$classNameFilters")
        jvmOpts.forEach { append(" --jvmopt=$it") }
        append(" --nocache_test_results --test_output=streamed")
    }
