// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import org.jetbrains.bazel.wasmjs.test.harness.ExitCodes
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

/**
 * Reports the already-rendered test run [outcome] in XML format, dumps [infrastructureLog] in case
 * of non-successful runs, and returns the corresponding Bazel exit code value.
 */
internal fun reportTestRun(
  entrypointModulePath: String,
  outcome: TestRunOutcome,
  infrastructureLog: InfrastructureLog,
  xmlOutputFile: Path?,
  undeclaredOutputsDir: Path?,
): Int = when (outcome) {
  is TestRunOutcome.Completed -> {
    echoSummary(entrypointModulePath, outcome.suites, outcome.orphanOutput)
    writeXmlReport(xmlOutputFile, outcome.suites)
    when {
      // Before the failure check: the no-tests verdict comes with its own synthetic errored case.
      outcome.executedNoTests -> {
        writeInfrastructureLog(undeclaredOutputsDir, infrastructureLog)
        System.err.println("wasmjs_test executed no test ($NO_TEST_EXECUTED_DETAILS)")
        ExitCodes.NO_TESTS_EXECUTED
      }
      outcome.suites.failedCount + outcome.suites.erroredCount > 0 -> {
        writeInfrastructureLog(undeclaredOutputsDir, infrastructureLog)
        ExitCodes.TESTS_FAILED
      }
      else -> ExitCodes.SUCCESS
    }
  }
  is TestRunOutcome.InfrastructureFailure -> {
    // What the page printed before the run broke is echoed like a completed run's orphan output.
    outcome.orphanOutput.forEach { println(it) }
    writeXmlReport(xmlOutputFile, outcome.suites)
    writeInfrastructureLog(undeclaredOutputsDir, infrastructureLog)
    System.err.println("wasmjs_test infrastructure failure: ${outcome.reason}")
    System.err.print(infrastructureLog.toString())
    ExitCodes.INFRASTRUCTURE_FAILURE
  }
}

private fun writeXmlReport(xmlOutputFile: Path?, suites: List<TestSuiteResult>) {
  xmlOutputFile?.let { xmlFile ->
    xmlFile.parent?.createDirectories()
    xmlFile.outputStream().use { out -> writeJUnitXml(suites, out) }
  }
}

private fun writeInfrastructureLog(undeclaredOutputsDir: Path?, infrastructureLog: InfrastructureLog) {
  undeclaredOutputsDir?.let { outputsDir ->
    outputsDir.createDirectories()
    outputsDir.resolve("infrastructure.log").writeText(infrastructureLog.toString())
  }
}

private fun echoSummary(entrypointModulePath: String, suites: List<TestSuiteResult>, orphanOutput: List<String>) {
  orphanOutput.forEach { println(it) }
  suites.asSequence().flatMap { it.tests }.forEach { test ->
    when (test.status) {
      is TestStatus.Failed -> {
        System.err.println("FAILED ${test.className}.${test.name}: ${test.status.message}")
        System.err.println(test.status.details)
      }
      is TestStatus.Errored -> {
        System.err.println("ERRORED ${test.className}.${test.name}: ${test.status.message}")
        System.err.println(test.status.details)
      }
      else -> Unit
    }
  }
  System.err.println("wasmjs_test $entrypointModulePath: ${suites.testCount} tests, ${suites.failedCount} failed, ${suites.erroredCount} errored, ${suites.ignoredCount} ignored")
}
