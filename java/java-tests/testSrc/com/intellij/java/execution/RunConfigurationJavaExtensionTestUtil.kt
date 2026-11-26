// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution

import com.intellij.JavaTestUtil
import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestFixture
import com.intellij.execution.OutputCheckerFactory
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executionTestFixture
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

/**
 * Creates the execution fixture of the run configuration extension tests.
 * The test app is `tinyApp` of the Java test data. The tests do not check the output of the checker.
 */
internal fun runConfigurationExtensionTestFixture(): TestFixture<ExecutionTestFixture> =
  executionTestFixture("${JavaTestUtil.getJavaTestDataPath()}/tinyApp/", OutputCheckerFactory { _, _ -> OutputChecker({ "" }, { "" }) })

/**
 * Runs [configuration] with only [UnApplicableConfigurationExtension] registered and waits for the end of the process.
 * When [expectedOutput] is not `null`, it compares the standard output of the process with it.
 */
internal fun doTestOnlyApplicableConfigurationExtensionsShouldBeProcessed(
  execution: ExecutionTestFixture,
  configuration: RunConfiguration,
  expectedOutput: String? = null,
) {
  ExtensionTestUtil.maskExtensions(RunConfigurationExtension.EP_NAME, listOf(UnApplicableConfigurationExtension()), execution.disposable)
  val stdout = runAndWaitForTermination(execution, configuration)
  if (expectedOutput != null) {
    assertEquals(expectedOutput, stdout)
  }
}

/**
 * Runs [configuration] with the default run executor from the EDT and waits for the end of the process.
 *
 * @return the standard output of the process
 */
internal fun runAndWaitForTermination(execution: ExecutionTestFixture, configuration: RunConfiguration): String {
  lateinit var processHandler: ProcessHandler
  val executionStarted = Semaphore(1)
  val outputReader = CapturingProcessAdapter()
  val environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build(
    ProgramRunner.Callback {
      processHandler = it.processHandler!!
      processHandler.addProcessListener(outputReader)
      executionStarted.up()
    })
  timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      environment.runner.execute(environment)
    }
  }
  executionStarted.waitFor()
  execution.waitProcess(processHandler)
  assertTrue(processHandler.isProcessTerminated)
  return outputReader.output.stdout
}

/** A run configuration extension that fails the test when the run calls it. */
internal class UnApplicableConfigurationExtension : RunConfigurationExtension() {
  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = false

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
    configuration: T & Any,
    params: JavaParameters,
    runnerSettings: RunnerSettings?,
  ) {
    fail<Unit>("Should not be here")
  }

  override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
    fail<Unit>("Should not be here")
  }
}
