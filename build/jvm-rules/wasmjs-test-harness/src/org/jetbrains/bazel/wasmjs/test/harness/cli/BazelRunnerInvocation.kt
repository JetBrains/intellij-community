// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The outcome of [BazelRunnerInvocation.parse]: a prepared run, or the reason the invocation is unusable. */
internal sealed interface BazelInvocationParseResult {
  data class Valid(val invocation: BazelRunnerInvocation) : BazelInvocationParseResult
  data class Invalid(val error: String) : BazelInvocationParseResult
}

/**
 * The prepared runner invocation: everything read and validated before the run starts. [parse]
 * consumes one `--option=value` per flagfile line, written by the `_wasmjs_browser_test` rule,
 * and prepares the run — it reads the browser flagfile and creates the browser profile directory.
 */
internal data class BazelRunnerInvocation(
  /**
   * The full browser command line: the executable from `--browser-binary` (already resolved to a
   * file by the rule — the harness carries no browser knowledge) followed by the arguments of
   * `--browser-flagfile` (one per line), with `${BROWSER_PROFILE_DIR}` references replaced by the
   * resolved (and created) `--browser-profile-dir`.
   */
  val browserCommand: List<String>,
  val staticContentDir: Path,
  val entrypoint: String,
  val configurationScripts: List<String>,
  val npmPackages: List<String>,
  val importRemaps: Map<String, String>,
  val awaitedImports: List<Pair<String, String>>,
  /** Time until the browser starts and exposes its DevTools endpoint. */
  val browserSetupTimeout: Duration,
  /**
   * How long the harness still waits for output before it calls the run finished: measured from the last
   * console line the page printed, and only counted once no test and no suite is still open — any further
   * output resets it. kotlin-test never announces the end of a run, so this is how the harness infers it.
   * A target whose async gap between two suites outlasts this grace period has its run declared over
   * early — it reports what it collected, without the suites that never came.
   */
  val testCompletionGracePeriod: Duration,
) {
  companion object {
    // The phase defaults, the single source of truth. Only the completion grace period is tunable per
    // target, through `wasmjs_test`'s `test_completion_grace_period_ms`; `--browser-setup-timeout-ms`
    // exists for driving the harness by hand. Page load carries no phase timeout of its own: it is
    // bounded by the Bazel deadline derived from TEST_TIMEOUT, which the run loop observes.
    private val DEFAULT_BROWSER_SETUP_TIMEOUT = 30.seconds
    private val DEFAULT_TEST_COMPLETION_GRACE_PERIOD = 3.seconds

    private val KNOWN_OPTIONS = setOf(
      "--browser-binary",
      "--browser-flagfile",
      "--browser-profile-dir",
      "--static-content-dir",
      "--entrypoint",
      "--configuration-script",
      "--npm-package",
      "--import-remap",
      "--awaited-import",
      "--browser-setup-timeout-ms",
      "--test-completion-grace-period-ms",
    )

    fun parse(args: List<String>, environment: BazelTestEnvironment): BazelInvocationParseResult = try {
      val valuesByOption = actualArgs(args, environment)
        //@formatter:off
        .filter(String::isNotBlank)
        .map { argument ->
          require(argument.startsWith("--") && argument.contains('=')) { "expected --option=value, got: $argument" }
          argument.split('=', limit = 2)
        }
        .groupBy({ (option, _) -> option }, { (_, value) -> value })
        //@formatter:on
      (valuesByOption.keys - KNOWN_OPTIONS).let { unknown ->
        require(unknown.isEmpty()) { "unknown options: ${unknown.joinToString()}" }
      }
      BazelInvocationParseResult.Valid(BazelRunnerInvocation(
        browserCommand = browserCommand(
          browserBinary = valuesByOption.single("--browser-binary"),
          browserFlagfile = valuesByOption.single("--browser-flagfile"),
          browserProfileDir = valuesByOption.single("--browser-profile-dir"),
          environment = environment,
        ),
        staticContentDir = resolveRunnerPath(valuesByOption.single("--static-content-dir"), environment),
        entrypoint = valuesByOption.single("--entrypoint"),
        configurationScripts = valuesByOption["--configuration-script"].orEmpty(),
        npmPackages = valuesByOption["--npm-package"].orEmpty(),
        importRemaps = valuesByOption["--import-remap"].orEmpty().associate { value -> value.toPair("--import-remap") },
        awaitedImports = valuesByOption["--awaited-import"].orEmpty().map { value -> value.toPair("--awaited-import") },
        browserSetupTimeout = valuesByOption.timeoutOrNull("--browser-setup-timeout-ms") ?: DEFAULT_BROWSER_SETUP_TIMEOUT,
        testCompletionGracePeriod = valuesByOption.timeoutOrNull("--test-completion-grace-period-ms")
                                    ?: DEFAULT_TEST_COMPLETION_GRACE_PERIOD,
      ))
    }
    catch (e: IllegalArgumentException) {
      BazelInvocationParseResult.Invalid(e.message ?: "invalid invocation: $e")
    }
    catch (e: IOException) {
      BazelInvocationParseResult.Invalid("cannot prepare the run invocation: $e")
    }

    private fun actualArgs(args: List<String>, environment: BazelTestEnvironment): List<String> = when (args.size) {
      0 -> {
        // `bazel test` starts the runner without arguments: the `_wasmjs_browser_test` rule writes the
        // full invocation as `--option=value` lines in a flagfile next to the test executable, located
        // here through the standard `TEST_BINARY` env var
        val flagfile = requireNotNull(environment.defaultFlagfileRlocationPath()) {
          "started bare outside `bazel test` (TEST_BINARY/TEST_WORKSPACE not set); pass --flagfile=<path> or explicit options"
        }
        resolveRunnerPath(flagfile, environment).readLines()
      }
      1 -> {
        // A manual invocation (`bazel run`) passes either a single `--flagfile=<path>` (same format, rlocation or filesystem path)...
        val flagFileArg = args.single()
        require(flagFileArg.startsWith(FLAGFILE_FLAG)) {
          "must be run with a single $FLAGFILE_FLAG<path> argument"
        }
        val flagfile = flagFileArg.removePrefix(FLAGFILE_FLAG)
        resolveRunnerPath(flagfile, environment).readLines()
      }
      else -> {
        // ...or the options directly.
        args
      }
    }

    private const val FLAGFILE_FLAG = "--flagfile="

    /** An optional `<option>=<milliseconds>`; null when the flagfile does not carry that option. */
    private fun Map<String, List<String>>.timeoutOrNull(option: String): Duration? =
      this[option].orEmpty().also { values -> require(values.size <= 1) { "$option is allowed at most once, got ${values.size}" } }
        .singleOrNull()?.let { value ->
          requireNotNull(value.toLongOrNull()?.takeIf { it > 0 }) {
            "$option expects a positive number of milliseconds, got: $value"
          }.milliseconds
        }

    private fun Map<String, List<String>>.single(option: String): String {
      val values = this[option].orEmpty()
      require(values.size == 1) { "$option is required exactly once, got ${values.size}" }
      return values.single()
    }

    private fun String.toPair(option: String): Pair<String, String> {
      val split = split('=', limit = 2)
      require(split.size == 2) { "$option expects <key>=<value>, got: $this" }
      return split[0] to split[1]
    }
  }
}

/** Assembles [BazelRunnerInvocation.browserCommand] from the raw browser options. */
private fun browserCommand(
  browserBinary: String,
  browserFlagfile: String,
  browserProfileDir: String,
  environment: BazelTestEnvironment,
): List<String> {
  val binary = resolveRunnerPath(browserBinary, environment)
  require(binary.isRegularFile()) {
    "browser binary does not exist: $binary (from --browser-binary=$browserBinary)"
  }
  val profileDir = resolveBrowserProfileDir(browserProfileDir, environment.tmpDir)
  profileDir.createDirectories()
  val arguments = resolveRunnerPath(browserFlagfile, environment).readLines().filter(String::isNotBlank)
  return listOf(binary.toString()) + substituteBrowserProfileDir(arguments, profileDir)
}

/** A relative `--browser-profile-dir` lives under `TEST_TMPDIR` (or a fresh temp dir outside `bazel test`). */
internal fun resolveBrowserProfileDir(raw: String, tmpDir: Path?): Path = Path(raw).let { path ->
  when {
    path.isAbsolute -> path
    else -> (tmpDir ?: createTempDirectory("wasmjs-test")).resolve(path)
  }
}

internal fun substituteBrowserProfileDir(arguments: List<String>, profileDir: Path): List<String> =
  arguments.map { argument -> argument.replace("\${BROWSER_PROFILE_DIR}", profileDir.toString()) }
