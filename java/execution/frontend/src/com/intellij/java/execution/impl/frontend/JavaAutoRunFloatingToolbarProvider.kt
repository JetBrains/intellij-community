// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.impl.frontend

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.java.execution.impl.shared.JavaAutoRunFloatingToolbarApi
import com.intellij.java.frontback.impl.JavaFrontbackBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.project.projectId
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shows a floating toolbar when tests run automatically.
 */
@ApiStatus.Internal
class JavaAutoRunFloatingToolbarProvider : FloatingToolbarProvider {
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

    updateFloatingToolbarVisibility(component, false)

    project.service<AutoRunFloatingToolbarService>().scope.launch {
      JavaAutoRunFloatingToolbarApi.getInstance().toolbarStatus(project.projectId())?.collect {
        state -> runInEdt {
          updateFloatingToolbarVisibility(component, state.autoTestEnabled && state.toolbarEnabled)
        }
      }
    }
  }

  private fun updateFloatingToolbarVisibility(component: FloatingToolbarComponent, visible: Boolean) {
    if (visible) {
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
    panel.add(JBLabel(JavaFrontbackBundle.message("auto.run.floating.toolbar.text")), constraints.next().insets(0, 8, 0, 8))

    val disableButton = ActionButton(DisableAction(), null, ActionPlaces.EDITOR_FLOATING_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    disableButton.putClientProperty(Toggleable.SELECTED_KEY, true)
    panel.add(disableButton, constraints.next())
    panel.isOpaque = false

    return panel
  }
}

private class DisableAction : AnAction(IdeBundle.message("button.disable"), JavaFrontbackBundle.message("auto.run.floating.toolbar.disable.action"), AllIcons.Actions.RerunAutomatically) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.service<AutoRunFloatingToolbarService>().scope.launch {
      JavaAutoRunFloatingToolbarApi.getInstance().disableAllAutoTests(project.projectId())
    }
  }
}

private class HideAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.service<AutoRunFloatingToolbarService>().scope.launch {
      JavaAutoRunFloatingToolbarApi.getInstance().setToolbarEnabled(project.projectId(), false)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Actions.Close
    e.presentation.text = IdeBundle.message("tooltip.hide")
  }
}

@Service(Service.Level.PROJECT)
private class AutoRunFloatingToolbarService(val scope: CoroutineScope)