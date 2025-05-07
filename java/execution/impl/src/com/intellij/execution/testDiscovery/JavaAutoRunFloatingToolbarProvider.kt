// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.build.BuildView
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.autotest.AutoTestListener
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManager.getInstanceIfCreated
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.java.JavaBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.application
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shows a floating toolbar when tests run automatically.
 */
@ApiStatus.Internal
class JavaAutoRunFloatingToolbarProvider : FloatingToolbarProvider {

  private var configuration: RunContentDescriptor? = null

  override val backgroundAlpha: Float = JBUI.CurrentTheme.FloatingToolbar.TRANSLUCENT_BACKGROUND_ALPHA

  override val autoHideable: Boolean = false

  override val actionGroup: ActionGroup
    get() = DefaultActionGroup(DisableAutoTestAction()).apply {
      add(HideAction())
    }

  override fun isApplicable(dataContext: DataContext): Boolean {
    return isInsideMainEditor(dataContext)
           && dataContext.getData(CommonDataKeys.EDITOR)?.editorKind == EditorKind.MAIN_EDITOR
  }

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    val autoRunManager = JavaAutoRunManager.getInstance(project)

    application.invokeLater {
      updateFloatingToolbarVisibility(project, component, autoRunManager)
    }

    project.messageBus.connect(parentDisposable).subscribe(AutoTestListener.TOPIC, object: AutoTestListener {
      override fun autoTestStatusChanged() {
        updateFloatingToolbarVisibility(project, component, autoRunManager)
        // Picks up the current descriptor when auto-test is enabled (auto-test is always disabled on project opening)
        updateCurrentConfiguration(project, autoRunManager, component)
      }
    })

    // The descriptor is disposed and created again after each run.
    project.messageBus.connect(parentDisposable).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        updateCurrentConfiguration(project, autoRunManager, component)
      }
    })
  }

  /**
   * Keeps track of the current auto-run test descriptor.
   */
  private fun updateCurrentConfiguration(project: Project, autoRunManager: JavaAutoRunManager, component: FloatingToolbarComponent) {
    val content = RunContentManager.getInstance(project).getAllDescriptors().firstOrNull { autoRunManager.isAutoTestEnabled(it) }
    if (content != configuration) { configuration = content; } else { return }
    if (content == null) {
      updateFloatingToolbarVisibility(project, component, autoRunManager)
      return
    }

    content.whenDisposed {
      application.invokeLater {
        updateFloatingToolbarVisibility(project, component, autoRunManager)
      }
    }

    getConsoleProperties(project)?.addListener(TestConsoleProperties.SHOW_AUTO_TEST_TOOLBAR) {
      application.invokeLater {
        updateFloatingToolbarVisibility(project, component, autoRunManager)
      }
    }
  }

  private fun updateFloatingToolbarVisibility(project: Project, component: FloatingToolbarComponent, autoRunManager: JavaAutoRunManager) {
    val isToolbarEnabled = isAutoTestToolbarEnabled(project)
    val hasEnabledAutoTests = autoRunManager.hasEnabledAutoTests()
    if (isToolbarEnabled && hasEnabledAutoTests) {
      component.autoHideable = true
    } else {
      component.autoHideable = false
      component.hideImmediately()
    }
  }
}

private class DisableAutoTestAction : AnAction(), CustomComponentAction, DumbAware {
  override fun actionPerformed(e: AnActionEvent) {}

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent  {
    val panel = JPanel(GridBagLayout())

    val constraints = GridBag()
    panel.add(JBLabel(JavaBundle.message("auto.test.on")), constraints.next().insets(0, 8, 0, 8))

    val disableButton = ActionButton(DisableAction(), null, ActionPlaces.EDITOR_FLOATING_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    disableButton.putClientProperty(Toggleable.SELECTED_KEY, true)
    panel.add(disableButton, constraints.next())
    panel.isOpaque = false

    return panel
  }
}

private class DisableAction : AnAction(IdeBundle.message("button.disable"), JavaBundle.message("disable.auto.test"), AllIcons.Actions.RerunAutomatically) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val manager = JavaAutoRunManager.getInstance(project)
    manager.disableAllAutoTests()
  }
}

private class HideAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val properties = getConsoleProperties(project) ?: return
    TestConsoleProperties.SHOW_AUTO_TEST_TOOLBAR.set(properties, false)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Actions.Close
    e.presentation.text = IdeBundle.message("tooltip.hide")
  }
}

private fun getConsoleProperties(project: Project): TestConsoleProperties? {
  val content = getInstanceIfCreated(project)?.selectedContent ?: return null
  val console = getSMTRunnerConsoleView(content.executionConsole) ?: return null
  return console.properties
}

private fun isAutoTestToolbarEnabled(project: Project): Boolean {
  val properties = getConsoleProperties(project) ?: return false
  return TestConsoleProperties.SHOW_AUTO_TEST_TOOLBAR.get(properties)
}

private fun getSMTRunnerConsoleView(console: ExecutionConsole): SMTRunnerConsoleView? {
  return when (console) {
    is SMTRunnerConsoleView -> console
    is ConsoleViewWithDelegate -> getSMTRunnerConsoleView(console.delegate)
    is BuildView -> console.consoleView?.let { getSMTRunnerConsoleView(it) }
    else -> null
  }
}