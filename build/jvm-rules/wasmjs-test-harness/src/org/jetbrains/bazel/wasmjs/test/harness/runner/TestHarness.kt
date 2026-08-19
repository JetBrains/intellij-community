// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.coroutines.CancellationException
import org.jetbrains.bazel.wasmjs.test.harness.ExitCodes
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

object TestHarness {
  /**
   * Run tests of the [entrypointModulePath], filtered by [testFilters] if specified, and write test report in [xmlOutputFile].
   */
  suspend fun runTestsAndReport(
    browserCommand: List<String>,
    staticContentDir: Path,
    entrypointModulePath: String,
    configurationScriptPaths: List<String>,
    npmPackages: List<String>,
    importRemaps: Map<String, String> = emptyMap(),
    awaitedImports: List<Pair<String, String>> = emptyList(),
    testFilters: List<String>,
    bazelDeadline: ComparableTimeMark?,
    browserSetupTimeout: Duration,
    testCompletionGracePeriod: Duration,
    xmlOutputFile: Path?,
    undeclaredOutputsDir: Path?,
  ): Int {
    val infrastructureLog = InfrastructureLog()
    val testOutcome = try {
      runTests(
        browserCommand = browserCommand,
        staticContentDir = staticContentDir,
        // The page is generated at test runtime (not as a build action): it depends only on files
        // already in the runfiles tree, and keeping it here leaves the rule with symlinks only.
        indexHtml = generateIndexHtml(
          entrypointModulePath = entrypointModulePath,
          configurationScriptPaths = configurationScriptPaths,
          npmPackageEntries = npmPackages.associateWith { specifier ->
            resolveNpmEntry(specifier, npmPackageJson(staticContentDir, specifier))
          },
          importRemaps = importRemaps,
          awaitedImports = awaitedImports,
        ),
        testFilters = testFilters,
        infrastructureLog = infrastructureLog,
        bazelDeadline = bazelDeadline,
        browserSetupTimeout = browserSetupTimeout,
        testCompletionGracePeriod = testCompletionGracePeriod,
      )
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      TestRunOutcome.InfrastructureFailure(reason = e.stackTraceToString(), suites = emptyList())
    }
    return try {
      reportTestRun(
        entrypointModulePath = entrypointModulePath,
        outcome = testOutcome,
        infrastructureLog = infrastructureLog,
        xmlOutputFile = xmlOutputFile,
        undeclaredOutputsDir = undeclaredOutputsDir,
      )
    }
    catch (e: IOException) {
      // A run whose report cannot be written must still fail loudly, and as what it is: an
      // infrastructure failure, not an invocation error or a bare crash.
      System.err.println("wasmjs-test-harness: cannot write the test report: $e")
      ExitCodes.INFRASTRUCTURE_FAILURE
    }
  }
}

/**
 * The staged `package.json` of an npm package, the first thing the run reads out of the static
 * root. Its absence means the root itself is wrong far more often than the package is, so it is
 * named here rather than left to a bare `NoSuchFileException` from the read.
 */
private fun npmPackageJson(staticContentDir: Path, specifier: String): String {
  val packageJson = staticContentDir.resolve("node_modules/$specifier/package.json")
  require(packageJson.isRegularFile()) {
    "npm package $specifier is not staged in the static content root: no $packageJson"
  }
  return packageJson.readText()
}
