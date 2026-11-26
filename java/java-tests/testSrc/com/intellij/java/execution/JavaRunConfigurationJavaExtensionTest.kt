// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution

import com.intellij.execution.Executor
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.JavaConsoleDecorator
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

private val JAVA_CONSOLE_DECORATOR_EP = ExtensionPointName<JavaConsoleDecorator>("com.intellij.java.consoleDecorator")

@TestApplication
class JavaRunConfigurationJavaExtensionTest {
  private val execution by runConfigurationExtensionTestFixture()

  @Test
  fun `test only applicable configuration extensions should be processed`() {
    doTestOnlyApplicableConfigurationExtensionsShouldBeProcessed(execution, createJavaApplicationRunConfiguration(), "Hello World!")
  }

  @Test
  fun `test the run creates the console on the EDT`() {
    val consoleCreatedOnEdt = CopyOnWriteArrayList<Boolean>()
    val decorator = object : JavaConsoleDecorator() {
      override fun decorateConsole(console: ConsoleView, runConfiguration: RunConfigurationBase<*>, executor: Executor): ConsoleView {
        consoleCreatedOnEdt.add(EDT.isCurrentThreadEdt())
        return console
      }
    }
    ExtensionTestUtil.maskExtensions(JAVA_CONSOLE_DECORATOR_EP, listOf(decorator), execution.disposable)

    runAndWaitForTermination(execution, createJavaApplicationRunConfiguration())

    assertEquals(listOf(true), consoleCreatedOnEdt)
  }

  @Test
  fun `test the state executes in a background read action`() {
    val configuration = createJavaApplicationRunConfiguration()
    val environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build()

    val result = timeoutRunBlocking {
      withContext(Dispatchers.Default) {
        ReadAction.computeBlocking<_, Throwable> { environment.state!!.execute(environment.executor, environment.runner) }
      }
    }

    val processHandler = result!!.processHandler!!
    processHandler.startNotify()
    execution.waitProcess(processHandler)
    assertNotNull(result.executionConsole)
    Disposer.dispose(result.executionConsole)
  }

  private fun createJavaApplicationRunConfiguration() =
    ApplicationConfiguration("HelloWorldApplicationConfiguration", execution.project).apply {
      setModule(execution.module)
      mainClassName = "HelloWorld"
    }
}
