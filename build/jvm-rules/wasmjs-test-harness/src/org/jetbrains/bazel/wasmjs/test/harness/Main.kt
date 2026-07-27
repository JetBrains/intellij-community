// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness

import org.jetbrains.bazel.wasmjs.test.harness.cli.BazelInvocationParseResult
import org.jetbrains.bazel.wasmjs.test.harness.cli.BazelRunnerInvocation
import org.jetbrains.bazel.wasmjs.test.harness.cli.BazelTestEnvironment
import org.jetbrains.bazel.wasmjs.test.harness.cli.softDeadline
import org.jetbrains.bazel.wasmjs.test.harness.runner.TestHarness
import kotlin.system.exitProcess
import kotlin.time.TimeSource

suspend fun main(args: Array<String>) {
  val environment = BazelTestEnvironment.fromSystemEnvironment()
  environment.warnings.forEach(System.err::println)
  val exitCode = when {
    environment.unsupportedFeatures.isNotEmpty() -> {
      environment.unsupportedFeatures.forEach { feature ->
        System.err.println("wasmjs_test does not support $feature")
      }
      ExitCodes.INVOCATION_ERROR
    }
    // Only a failed invocation phase is an invocation error — once the run is on, [TestHarness]
    // maps its failures to an infrastructure-failure report instead.
    else -> when (val parsed = BazelRunnerInvocation.parse(args.toList(), environment)) {
      is BazelInvocationParseResult.Invalid -> {
        System.err.println("wasmjs-test-harness: ${parsed.error}")
        ExitCodes.INVOCATION_ERROR
      }
      is BazelInvocationParseResult.Valid -> TestHarness.runTestsAndReport(
        browserCommand = parsed.invocation.browserCommand,
        staticContentDir = parsed.invocation.staticContentDir,
        entrypointModulePath = parsed.invocation.entrypoint,
        configurationScriptPaths = parsed.invocation.configurationScripts,
        npmPackages = parsed.invocation.npmPackages,
        importRemaps = parsed.invocation.importRemaps,
        awaitedImports = parsed.invocation.awaitedImports,
        testFilters = environment.testFilters,
        bazelDeadline = environment.timeout?.let { timeout -> TimeSource.Monotonic.markNow() + softDeadline(timeout) },
        browserSetupTimeout = parsed.invocation.browserSetupTimeout,
        testCompletionGracePeriod = parsed.invocation.testCompletionGracePeriod,
        xmlOutputFile = environment.xmlOutputFile,
        undeclaredOutputsDir = environment.undeclaredOutputsDir,
      )
    }
  }
  exitProcess(exitCode)
}

/** Exit codes of the runner; Bazel itself only distinguishes zero from non-zero. */
object ExitCodes {
  const val SUCCESS: Int = 0
  const val TESTS_FAILED: Int = 1
  const val NO_TESTS_EXECUTED: Int = 2
  const val INFRASTRUCTURE_FAILURE: Int = 3
  const val INVOCATION_ERROR: Int = 4
}
