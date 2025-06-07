// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.impl.backend

import com.intellij.build.BuildView
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testDiscovery.JavaAutoRunManager
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.autotest.AutoTestListener
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManager.getInstanceIfCreated
import com.intellij.java.execution.impl.shared.JavaAutoRunFloatingToolbarStatus
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps track of the current auto-run test descriptor.
 */
@Service(Service.Level.PROJECT)
class JavaAutoRunTrackerService(val project: Project, val scope: CoroutineScope) {

  private var autoRunConfiguration: RunContentDescriptor? = null
  val flow: MutableStateFlow<JavaAutoRunFloatingToolbarStatus> = MutableStateFlow(JavaAutoRunFloatingToolbarStatus(false, false))

  fun registerListeners() {
    val autoRunManager = JavaAutoRunManager.getInstance(project)
    project.messageBus.connect(scope).subscribe(AutoTestListener.TOPIC, object : AutoTestListener {
      override fun autoTestStatusChanged() {
        updateCurrentConfiguration(project, autoRunManager)
      }
    })

    project.messageBus.connect(scope).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        updateCurrentConfiguration(project, autoRunManager)
      }
    })
  }

  fun setFloatingToolbarEnabled(enabled: Boolean) {
    val properties = getConsoleProperties(project) ?: return
    TestConsoleProperties.SHOW_AUTO_TEST_TOOLBAR.set(properties, enabled)
    autoTestStatusChanged()
  }

  fun disableAutoTests() {
    scope.launch(Dispatchers.EDT) {
      JavaAutoRunManager.getInstance(project).disableAllAutoTests()
    }
  }

  fun updateCurrentConfiguration(project: Project, autoRunManager: JavaAutoRunManager) {
    scope.launch(Dispatchers.EDT) {
      val content = RunContentManager.getInstance(project).getAllDescriptors().firstOrNull { autoRunManager.isAutoTestEnabled(it) }
      if (content == autoRunConfiguration) return@launch
      autoRunConfiguration = content

      autoTestStatusChanged()

      if (content == null) return@launch

      content.whenDisposed {
        application.invokeLater {
          autoTestStatusChanged()
        }
      }

      getConsoleProperties(project)?.addListener(TestConsoleProperties.SHOW_AUTO_TEST_TOOLBAR) {
        autoTestStatusChanged()
      }
    }
  }

  private fun autoTestStatusChanged() {
    val floatingToolbarEnabled = when(autoRunConfiguration) {
      null -> false
      else -> getConsoleProperties(project)?.let { TestConsoleProperties.SHOW_AUTO_TEST_TOOLBAR.get(it) } ?: false
    }
    flow.value = JavaAutoRunFloatingToolbarStatus(autoRunConfiguration != null, floatingToolbarEnabled)
  }

  private fun getConsoleProperties(project: Project): TestConsoleProperties? {
    val content = getInstanceIfCreated(project)?.selectedContent ?: return null
    val console = getSMTRunnerConsoleView(content.executionConsole) ?: return null
    return console.properties
  }

  private fun getSMTRunnerConsoleView(console: ExecutionConsole): SMTRunnerConsoleView? {
    return when (console) {
      is SMTRunnerConsoleView -> console
      is ConsoleViewWithDelegate -> getSMTRunnerConsoleView(console.delegate)
      is BuildView -> console.consoleView?.let { getSMTRunnerConsoleView(it) }
      else -> null
    }
  }
}

private class JavaAutoRunTrackerActivity() : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<JavaAutoRunTrackerService>().registerListeners()
  }
}