// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestCase
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.Semaphore

abstract class RunConfigurationJavaExtensionManagerTestCase : ExecutionTestCase() {
  override fun initOutputChecker(): OutputChecker = OutputChecker("", "")

  protected fun doTestOnlyApplicableConfigurationExtensionsShouldBeProcessed(configuration: RunConfiguration,
                                                                             expectedOutput: String? = null) {
    ExtensionTestUtil.maskExtensions(RunConfigurationExtension.EP_NAME, listOf(UnApplicableConfigurationExtension()), testRootDisposable)
    lateinit var processHandler: ProcessHandler
    val executionStarted = Semaphore(1)
    val outputReader = CapturingProcessAdapter()
    val environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build(
      ProgramRunner.Callback {
        processHandler = it.processHandler!!
        processHandler.addProcessListener(outputReader)
        executionStarted.up()
      })
    runInEdtAndWait {
      environment.runner.execute(environment)
    }
    executionStarted.waitFor()
    waitProcess(processHandler)
    assertTrue(processHandler.isProcessTerminated)
    if (expectedOutput != null) {
      assertEquals(expectedOutput, outputReader.output.stdout)
    }
  }

  protected class UnApplicableConfigurationExtension : RunConfigurationExtension() {
    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = false
    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T,
                                                                     params: JavaParameters,
                                                                     runnerSettings: RunnerSettings?) {
      fail("Should not be here")
    }


    override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
      fail("Should not be here")
    }
  }

}