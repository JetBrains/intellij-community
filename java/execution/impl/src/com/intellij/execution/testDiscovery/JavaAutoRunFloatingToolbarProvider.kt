// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.execution.testDiscovery.JavaAutoRunFloatingToolbarService.JavaAutoRunFloatingToolbarState
import com.intellij.execution.testframework.autotest.AutoTestListener
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.java.JavaBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.components.JBLabel
import com.intellij.util.application
import com.intellij.util.ui.GridBag
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shows a floating toolbar when tests run automatically.
 */
@ApiStatus.Internal
class JavaAutoRunFloatingToolbarProvider : FloatingToolbarProvider {

  override val backgroundAlpha: Float = 0.9f

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
      updateFloatingToolbarVisibility(component, autoRunManager)
    }

    project.messageBus.connect(parentDisposable).subscribe(AutoTestListener.TOPIC, object: AutoTestListener {
      override fun autoTestStatusChanged() {
        updateFloatingToolbarVisibility(component, autoRunManager)
      }
      override fun autoTestSettingsChanged() {
        updateFloatingToolbarVisibility(component, autoRunManager)
      }
    })
  }

  private fun updateFloatingToolbarVisibility(component: FloatingToolbarComponent, autoRunManager: JavaAutoRunManager) {
    val isToolbarEnabled = service<JavaAutoRunFloatingToolbarService>().toolbarEnabled
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
    application.service<JavaAutoRunFloatingToolbarService>().toolbarEnabled = false
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Actions.Close
    e.presentation.text = IdeBundle.message("tooltip.hide")
  }
}


@Service(Service.Level.APP)
@State(name = "JavaAutoRunFloatingToolbarSettings", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
internal class JavaAutoRunFloatingToolbarService : SimplePersistentStateComponent<JavaAutoRunFloatingToolbarState>(
  JavaAutoRunFloatingToolbarState()
) {
  private val messageBusPublisher by lazy {
    application.messageBus.syncPublisher(AutoTestListener.TOPIC)
  }

  var toolbarEnabled: Boolean
    get() = state.toolbarEnabled
    set(value) {
      if (value != state.toolbarEnabled) {
        state.toolbarEnabled = value
        messageBusPublisher.autoTestSettingsChanged()
      }
    }

  class JavaAutoRunFloatingToolbarState() : BaseState() {
    var toolbarEnabled by property(true)
  }
}