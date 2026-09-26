// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import com.google.devtools.build.runfiles.Runfiles
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The standard Bazel test-runner environment variable contract
 * (https://bazel.build/reference/test-encyclopedia), the only environment the harness reads.
 * Everything else reaches the runner through flags.
 */
data class BazelTestEnvironment(
  val runfiles: Runfiles.Preloaded?,
  val testWorkspace: String?,
  val testBinary: String?,
  val xmlOutputFile: Path?,
  val undeclaredOutputsDir: Path?,
  val tmpDir: Path?,
  val testFilters: List<String>,
  val timeout: Duration?,
  val unsupportedFeatures: List<String>,
  val warnings: List<String>,
) {
  /**
   * The rlocation path of the flagfile the `_wasmjs_browser_test` rule writes next to the
   * test executable: `TEST_BINARY` is workspace-relative (it may traverse up into sibling
   * repositories), so it is rebased on `TEST_WORKSPACE` and lexically normalized. The rule
   * names the flagfile after the target, so the Windows launcher's `.exe` is stripped.
   */
  fun defaultFlagfileRlocationPath(): String? = when {
    testBinary == null || testWorkspace == null -> null
    else -> Path(testWorkspace).resolve("${testBinary.removeSuffix(".exe")}.flagfile").normalize().invariantSeparatorsPathString
  }

  companion object {
    fun from(environment: Map<String, String>): BazelTestEnvironment = BazelTestEnvironment(
      runfiles = preloadedRunfiles(environment),
      testWorkspace = environment["TEST_WORKSPACE"],
      testBinary = environment["TEST_BINARY"],
      xmlOutputFile = environment["XML_OUTPUT_FILE"]?.let(::Path),
      undeclaredOutputsDir = environment["TEST_UNDECLARED_OUTPUTS_DIR"]?.let(::Path),
      tmpDir = environment["TEST_TMPDIR"]?.let(::Path),
      testFilters = environment["TESTBRIDGE_TEST_ONLY"]?.split(',')?.filter(String::isNotBlank).orEmpty(),
      timeout = environment["TEST_TIMEOUT"]?.toLongOrNull()?.seconds,
      unsupportedFeatures = unsupportedFeatures(environment),
      warnings = warnings(environment),
    )

    fun fromSystemEnvironment(): BazelTestEnvironment = from(System.getenv())

    /** Bazel's runfiles library; absent outside a runfiles context (e.g. build actions). */
    private fun preloadedRunfiles(environment: Map<String, String>): Runfiles.Preloaded? = try {
      Runfiles.preload(environment)
    }
    catch (_: IOException) {
      null
    }

    /**
     * Bazel test features the harness deliberately does not implement; requesting one fails the
     * run loudly instead of silently ignoring the flag. Sharding support is signalled to Bazel by
     * touching `TEST_SHARD_STATUS_FILE`, which the harness never does.
     */
    private fun unsupportedFeatures(environment: Map<String, String>): List<String> = buildList {
      val testTotalShards = environment["TEST_TOTAL_SHARDS"]?.toIntOrNull()
      if (testTotalShards != null && testTotalShards > 0) {
        add("flag --test_sharding_strategy")
        add("BUILD.bazel test attribute: shard_count")
        add("env variable TEST_TOTAL_SHARDS")
      }
      if (environment["TESTBRIDGE_TEST_RUNNER_FAIL_FAST"] == "1") {
        add("flag --test_runner_fail_fast")
      }
    }

    /** Requested features the harness ignores without failing the run (semantics unaffected). */
    private fun warnings(environment: Map<String, String>): List<String> = listOfNotNull(
      "TEST_RANDOM_SEED is ignored: wasmjs_test does not randomize test order".takeIf {
        !environment["TEST_RANDOM_SEED"].isNullOrEmpty()
      },
    )
  }
}

/**
 * Resolves a runner flag value into a path: absolute paths and paths valid from the working
 * directory are taken as-is; anything else is treated as an rlocation path and resolved
 * through Bazel's runfiles library (which handles both the runfiles directory and the
 * manifest layouts).
 *
 * An rlocation path that resolves to nothing fails here. Carrying it on unresolved only defers the
 * failure to whichever read touches it first, where it surfaces as a `NoSuchFileException` on a
 * runfiles-root-relative path with nothing to say where that path came from.
 */
internal fun resolveRunnerPath(raw: String, environment: BazelTestEnvironment): Path {
  val direct = Path(raw)
  return when {
    direct.isAbsolute -> direct
    direct.exists() -> direct.toAbsolutePath()
    else -> requireNotNull(environment.runfiles?.withSourceRepository("")?.rlocation(raw)?.let(::Path)) {
      when (environment.runfiles) {
        null -> "cannot resolve $raw: it is not a path on disk, and there is no runfiles context to resolve it in"
        // Only files are runfiles: a directory that the rule never declared as an artifact of its
        // own has no entry, and the manifest layout cannot resolve it the way a directory can.
        else -> "cannot resolve the rlocation path $raw: no such entry in the runfiles of this test"
      }
    }
  }.normalize()
}
