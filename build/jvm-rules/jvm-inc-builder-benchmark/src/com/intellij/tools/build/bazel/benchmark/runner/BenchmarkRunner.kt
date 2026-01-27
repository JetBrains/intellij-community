// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.runner

import com.intellij.tools.build.bazel.benchmark.generator.ProjectGenerator
import com.intellij.tools.build.bazel.benchmark.output.CliReporter
import com.intellij.tools.build.bazel.benchmark.output.JsonReporter
import com.intellij.tools.build.bazel.benchmark.scenarios.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Main benchmark runner that orchestrates scenario execution.
 */
class BenchmarkRunner(
  private val config: BenchmarkConfig,
) {
  private val metricsCollector = MetricsCollector(
    bazelPath = config.bazelPath,
    workingDir = config.projectPath,
    verbose = config.verbose,
  )

  private val cliReporter = CliReporter()
  private val jsonReporter = JsonReporter()

  /**
   * Runs all configured scenarios and returns the results.
   */
  fun run(): BenchmarkResult {
    println("Starting benchmark with ${config.scenarios.size} scenarios...")
    println("Project: ${config.projectPath}")
    println("Target: ${config.target}")
    println()

    // Initial build to warm up Bazel and populate caches
    println("Performing initial build...")
    val initialBuild = metricsCollector.measureBuild(config.target, incremental = true)
    if (initialBuild.exitCode != 0) {
      System.err.println("Initial build failed!")
      System.err.println(initialBuild.errorOutput)
      throw RuntimeException("Initial build failed with exit code ${initialBuild.exitCode}")
    }
    println("Initial build completed in ${initialBuild.wallTimeMs}ms")
    println()

    val scenarioResults = config.scenarios.map { scenario ->
      runScenario(scenario)
    }

    val projectInfo = extractProjectInfo()

    return BenchmarkResult(
      timestamp = Instant.now(),
      config = config,
      projectInfo = projectInfo,
      scenarioResults = scenarioResults,
    )
  }

  private fun runScenario(scenario: Scenario): ScenarioResult {
    println("Running scenario: ${scenario.name}")
    println("  Description: ${scenario.description}")

    val incrementalMeasurements = mutableListOf<BuildMetrics>()
    val nonIncrementalMeasurements = mutableListOf<BuildMetrics>()

    // Warmup iterations
    repeat(config.warmupIterations) { iteration ->
      cliReporter.printProgress(scenario.name, iteration + 1, config.warmupIterations, isWarmup = true)
      runSingleMeasurement(scenario) // Discard warmup results
    }
    cliReporter.clearProgress()

    // Measurement iterations
    repeat(config.measurementIterations) { iteration ->
      cliReporter.printProgress(scenario.name, iteration + 1, config.measurementIterations, isWarmup = false)

      val measurement = runSingleMeasurement(scenario)
      incrementalMeasurements.add(measurement.incremental)
      nonIncrementalMeasurements.add(measurement.nonIncremental)
    }
    cliReporter.clearProgress()

    val result = ScenarioResult(
      scenarioName = scenario.name,
      incrementalMetrics = AggregatedMetrics(incrementalMeasurements),
      nonIncrementalMetrics = AggregatedMetrics(nonIncrementalMeasurements),
    )

    println("  Incremental mean: ${result.incrementalMetrics.meanMs}ms")
    println("  Non-incremental mean: ${result.nonIncrementalMetrics.meanMs}ms")
    println("  Speedup: ${String.format("%.2fx", result.speedup)}")
    println()

    return result
  }

  private fun runSingleMeasurement(scenario: Scenario): Measurement {
    // Setup scenario state
    scenario.setup()

    // Run incremental build
    val incMetrics = metricsCollector.measureBuild(config.target, incremental = true)

    // Clean incremental state to force non-incremental to start fresh
    metricsCollector.cleanIncrementalState(config.target)

    // Re-setup scenario state (since cleanIncrementalState may have built)
    scenario.cleanup()
    scenario.setup()

    // Run non-incremental build
    val nonIncMetrics = metricsCollector.measureBuild(config.target, incremental = false)

    // Cleanup
    scenario.cleanup()

    return Measurement(incMetrics, nonIncMetrics)
  }

  private fun extractProjectInfo(): ProjectInfo {
    val srcDir = config.projectPath.resolve("src")
    val fileCount = if (Files.exists(srcDir)) {
      srcDir.toFile().walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
        .count()
    } else 0

    val hasKotlin = srcDir.toFile().walkTopDown().any { it.extension == "kt" }
    val hasJava = srcDir.toFile().walkTopDown().any { it.extension == "java" }

    val language = when {
      hasKotlin && hasJava -> Language.MIXED
      hasKotlin -> Language.KOTLIN
      else -> Language.JAVA
    }

    return ProjectInfo(
      name = config.projectPath.fileName.toString(),
      fileCount = fileCount,
      language = language,
    )
  }

  /**
   * Writes results to JSON file if output path is configured.
   */
  fun writeResults(result: BenchmarkResult) {
    config.outputPath?.let { path ->
      jsonReporter.writeResults(result, path)
      println("Results written to: $path")
    }
  }

  /**
   * Prints results to console.
   */
  fun printResults(result: BenchmarkResult) {
    println(cliReporter.formatResult(result))
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val parsedArgs = parseArgs(args)

      when (parsedArgs.command) {
        Command.GENERATE -> runGenerate(parsedArgs)
        Command.RUN -> runBenchmark(parsedArgs)
        Command.COMPARE -> runCompare(parsedArgs)
        Command.HELP -> printHelp()
      }
    }

    private fun runGenerate(args: ParsedArgs) {
      val outputDir = Path.of(args.options["output"] ?: "generated")
      val generator = ProjectGenerator(outputDir)

      val language = when (args.options["language"]?.lowercase()) {
        "java" -> Language.JAVA
        "kotlin" -> Language.KOTLIN
        "mixed" -> Language.MIXED
        else -> Language.JAVA
      }

      val config = ProjectConfig(
        name = "${language.name.lowercase()}-benchmark",
        language = language,
        totalFiles = args.options["files"]?.toIntOrNull() ?: 100,
      )

      generator.generate(config)
    }

    private fun runBenchmark(args: ParsedArgs) {
      val projectPath = Path.of(args.options["project"] ?: "generated/java-benchmark")
      val target = args.options["target"] ?: "//:java-benchmark"

      val scenarios = buildList {
        val scenarioFilter = args.options["scenario"]
        if (scenarioFilter == null || scenarioFilter == "cold_build") {
          add(ColdBuildScenario(projectPath, args.options["bazel"] ?: "bazel"))
        }
        if (scenarioFilter == null || scenarioFilter == "no_op") {
          add(NoOpScenario(projectPath))
        }
        if (scenarioFilter == null || scenarioFilter == "impl_change") {
          add(ImplChangeScenario(projectPath))
        }
        if (scenarioFilter == null || scenarioFilter == "api_change") {
          add(ApiChangeScenario(projectPath))
        }
        if (scenarioFilter == null || scenarioFilter == "multi_change") {
          add(MultiChangeScenario(projectPath))
        }
      }

      val config = BenchmarkConfig(
        projectPath = projectPath,
        target = target,
        scenarios = scenarios,
        warmupIterations = args.options["warmup"]?.toIntOrNull() ?: 3,
        measurementIterations = args.options["iterations"]?.toIntOrNull() ?: 5,
        bazelPath = args.options["bazel"] ?: "bazel",
        outputPath = args.options["output"]?.let { Path.of(it) },
        verbose = args.options.containsKey("verbose"),
      )

      val runner = BenchmarkRunner(config)
      val result = runner.run()
      runner.printResults(result)
      runner.writeResults(result)
    }

    private fun runCompare(args: ParsedArgs) {
      val baselinePath = args.positional.getOrNull(0) ?: run {
        System.err.println("Error: baseline JSON file required")
        exitProcess(1)
      }
      val currentPath = args.positional.getOrNull(1) ?: run {
        System.err.println("Error: current JSON file required")
        exitProcess(1)
      }

      // Note: In a real implementation, we'd parse the JSON files
      // For now, just print the paths
      println("Comparing:")
      println("  Baseline: $baselinePath")
      println("  Current: $currentPath")
      println()
      println("(JSON parsing not yet implemented - please compare files manually)")
    }

    private fun printHelp() {
      println("""
        JVM Incremental Compilation Benchmark

        Usage: benchmark.sh <command> [options]

        Commands:
          generate    Generate synthetic test projects
          run         Run benchmark suite
          compare     Compare two benchmark results
          help        Show this help message

        Generate options:
          --files=N        Number of files to generate (default: 100)
          --language=LANG  Language: java, kotlin, mixed (default: java)
          --output=DIR     Output directory (default: generated)

        Run options:
          --project=PATH   Path to project to benchmark
          --target=TARGET  Bazel target to build (default: //:java-benchmark)
          --scenario=NAME  Run specific scenario only
          --warmup=N       Warmup iterations (default: 3)
          --iterations=N   Measurement iterations (default: 5)
          --output=FILE    Output JSON file
          --bazel=PATH     Path to bazel binary (default: bazel)
          --verbose        Verbose output

        Compare options:
          <baseline.json> <current.json>

        Scenarios:
          cold_build   - Initial build with no cache
          no_op        - Rebuild with no changes
          impl_change  - Single implementation file change
          api_change   - API class modification
          multi_change - Multiple file changes (10%)

        Examples:
          ./benchmark.sh generate --files=200 --language=kotlin
          ./benchmark.sh run --output=results.json
          ./benchmark.sh run --scenario=impl_change
          ./benchmark.sh compare baseline.json current.json
      """.trimIndent())
    }

    private fun parseArgs(args: Array<String>): ParsedArgs {
      val command = when (args.getOrNull(0)?.lowercase()) {
        "generate" -> Command.GENERATE
        "run" -> Command.RUN
        "compare" -> Command.COMPARE
        "help", "--help", "-h" -> Command.HELP
        else -> Command.RUN // Default to run
      }

      val options = mutableMapOf<String, String>()
      val positional = mutableListOf<String>()

      args.drop(1).forEach { arg ->
        when {
          arg.startsWith("--") -> {
            val parts = arg.substring(2).split("=", limit = 2)
            options[parts[0]] = parts.getOrElse(1) { "true" }
          }
          arg.startsWith("-") -> {
            options[arg.substring(1)] = "true"
          }
          else -> {
            positional.add(arg)
          }
        }
      }

      return ParsedArgs(command, options, positional)
    }
  }
}

private enum class Command {
  GENERATE,
  RUN,
  COMPARE,
  HELP,
}

private data class ParsedArgs(
  val command: Command,
  val options: Map<String, String>,
  val positional: List<String>,
)
