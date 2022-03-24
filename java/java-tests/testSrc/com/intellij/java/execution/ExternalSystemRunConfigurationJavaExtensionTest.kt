// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.FakeConfigurationFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.runInEdtAndWait
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItem
import org.junit.Assert.assertThat

class ExternalSystemRunConfigurationJavaExtensionTest : RunConfigurationJavaExtensionManagerTestCase() {
  override fun getTestAppPath(): String = ""
  override fun setUpModule() {
    // do nothing
  }

  fun `test ExecutionException thrown from RunConfigurationExtension#updateJavaParameters should terminate execution`() {
    ExtensionTestUtil.maskExtensions(RunConfigurationExtension.EP_NAME, listOf(CantUpdateJavaParametersExtension()), testRootDisposable)
    val configuration = createExternalSystemRunConfiguration()

    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String?, t: Throwable?, details: Array<out String>): Boolean =
        t !is FakeExecutionException  // don't fail this if `LOG.error()` was called for our exception somewhere
    }) {
      runInEdtAndWait {
        ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).buildAndExecute()
      }

      val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
        .map { it.content }
      assertThat(notifications, hasItem(containsString(FakeExecutionException.MESSAGE)))
    }
  }

  fun `test only applicable configuration extensions should be processed`() {
    doTestOnlyApplicableConfigurationExtensionsShouldBeProcessed(createExternalSystemRunConfiguration())
  }

  private fun createExternalSystemRunConfiguration() =
    ExternalSystemRunConfiguration(ProjectSystemId("FakeExternalSystem"), project, FakeConfigurationFactory(), "FakeConfiguration").apply {
      settings.externalProjectPath = "" // any string to prevent NPE
    }

  private companion object {

    class CantUpdateJavaParametersExtension : RunConfigurationExtension() {
      override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true
      override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T,
                                                                       params: JavaParameters,
                                                                       runnerSettings: RunnerSettings?) {
        throw FakeExecutionException()
      }

      override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
        // 'attachToProcess' is called after 'updateJavaParameters'
        fail("Should not be here")
      }
    }

    class FakeExecutionException : ExecutionException(MESSAGE) {
      companion object {
        const val MESSAGE = "Fake Execution Exception"
      }
    }
  }
}
