// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.isOfSameType
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.*
import java.awt.event.InputEvent
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

internal const val RUN_TOOLBAR_HEIGHT = 30
internal const val RUN_TOOLBAR_BORDER_HEIGHT = 5

private fun createRunActionToolbar(isCurrentConfigurationRunning: () -> Boolean): ActionToolbar {
  return ActionManager.getInstance().createActionToolbar(
    ActionPlaces.MAIN_TOOLBAR,
    ActionManager.getInstance().getAction("RunToolbarMainActionGroup") as ActionGroup,
    true
  ).apply {
    targetComponent = null
    setReservePlaceAutoPopupIcon(false)
    layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    if (this is ActionToolbarImpl) {
      isOpaque = false
      setMinimumButtonSize(JBUI.size(36, RUN_TOOLBAR_HEIGHT))
      setActionButtonBorder(JBUI.Borders.empty())
      setSeparatorCreator { RunToolbarSeparator(isCurrentConfigurationRunning) }
      setCustomButtonLook(RunWidgetButtonLook(isCurrentConfigurationRunning))
      border = null
    }
  }
}

private val runToolbarDataKey = Key.create<Boolean>("run-toolbar-data")

private class RedesignedRunToolbarWrapper : AnAction(), CustomComponentAction {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent): Unit = error("Should not be invoked")

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return createRunActionToolbar {
      presentation.getClientProperty(runToolbarDataKey) ?: false
    }.component.let {
      Wrapper(it).apply { border = JBUI.Borders.empty(RUN_TOOLBAR_BORDER_HEIGHT, 12, RUN_TOOLBAR_BORDER_HEIGHT, 2) }
    }
  }

  override fun update(e: AnActionEvent) {
    if (!Registry.`is`("ide.experimental.ui.redesigned.run.widget")) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabled = false

    e.presentation.putClientProperty(runToolbarDataKey, isSomeRunningNow(e))
  }

  private fun isSomeRunningNow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val selectedConfiguration: RunnerAndConfigurationSettings? = RunManager.getInstanceIfCreated(project)?.selectedConfiguration

    (selectedConfiguration?.configuration as? CompoundRunConfiguration)?.let {
      return it.hasRunningSingletons(null)
    }

    if (selectedConfiguration == null) {
      if (!RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project) || DumbService.isDumb(project)) {
        // cannot get current PSI file for the Run Current configuration in dumb mode
        return false
      }
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return false
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return false
      val runConfigsForCurrentFile = ExecutorRegistryImpl.ExecutorAction.getRunConfigsForCurrentFile(psiFile, false)
      val runningDescriptors = ExecutionManagerImpl.getInstance(project).getRunningDescriptors { runConfigsForCurrentFile.contains(it) }
      return !runningDescriptors.isEmpty()
    }
    else {
      val runningDescriptors = ExecutionManagerImpl.getInstance(project).getRunningDescriptors { it.isOfSameType(selectedConfiguration) }
      return !runningDescriptors.isEmpty()
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val data = presentation.getClientProperty(runToolbarDataKey) ?: return
    val dataPropertyName = "old-run-toolbar-data"
    val oldData = component.getClientProperty(dataPropertyName) as? Boolean
    if (oldData == null) {
      component.putClientProperty(dataPropertyName, data)
    }
    else if (data != oldData) {
      component.repaint()
      component.putClientProperty(dataPropertyName, data)
    }
  }
}

class RunToolbarTopLevelExecutorActionGroup : ActionGroup() {
  override fun isPopup() = false

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val topLevelRunActions = listOf(IdeActions.ACTION_DEFAULT_RUNNER, IdeActions.ACTION_DEFAULT_DEBUGGER).mapNotNull {
      ActionManager.getInstance().getAction(it)
    }
    return topLevelRunActions.toTypedArray()
  }
}

private class RunWidgetButtonLook(private val isCurrentConfigurationRunning: () -> Boolean) : IdeaActionButtonLook() {
  override fun getStateBackground(component: JComponent, state: Int): Color {

    val color = getRunWidgetBackgroundColor(isCurrentConfigurationRunning())

    return when (state) {
      ActionButtonComponent.NORMAL -> color
      ActionButtonComponent.PUSHED -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.PRESSED_BACKGROUND, color)
      else -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.HOVER_BACKGROUND, color)
    }
  }

  override fun paintBackground(g: Graphics, component: JComponent, @ActionButtonComponent.ButtonState state: Int) {
    val rect = Rectangle(component.size)
    val color = getStateBackground(component, state)

    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.color = color
      val arc = buttonArc.float
      val width = rect.width
      val height = rect.height

      val shape = when (component) {
        component.parent?.components?.lastOrNull() -> {
          val shape1 = RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), width.toFloat(), height.toFloat(), arc, arc)
          val shape2 = Rectangle2D.Float(rect.x.toFloat() - 1, rect.y.toFloat(), arc, height.toFloat())
          Area(shape1).also { it.add(Area(shape2)) }
        }
        component.parent?.components?.get(0) -> {
          val shape1 = RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), width.toFloat(), height.toFloat(), arc, arc)
          val shape2 = Rectangle2D.Float((rect.x + width).toFloat() - arc, rect.y.toFloat(), arc, height.toFloat())
          Area(shape1).also { it.add(Area(shape2)) }
        }
        else -> {
          Rectangle2D.Float(rect.x.toFloat() - 1, rect.y.toFloat(), width.toFloat() + 2, height.toFloat())
        }
      }

      g2.fill(shape)
    }
    finally {
      g2.dispose()
    }
  }


  override fun paintIcon(g: Graphics, actionButton: ActionButtonComponent, icon: Icon, x: Int, y: Int) {
    if (icon.iconWidth == 0 || icon.iconHeight == 0) {
      return
    }
    super.paintIcon(g, actionButton, IconUtil.toStrokeIcon(icon, JBUI.CurrentTheme.RunWidget.FOREGROUND), x, y)
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}
  override fun getButtonArc(): JBValue = JBValue.Float(8f)
}

internal const val MINIMAL_POPUP_WIDTH = 270
private abstract class TogglePopupAction : ToggleAction {

  constructor()

  constructor(@NlsActions.ActionText text: String?,
              @NlsActions.ActionDescription description: String?,
              icon: Icon?) : super(text, description, icon)

  override fun isSelected(e: AnActionEvent): Boolean {
    return Toggleable.isSelected(e.presentation)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!state) return
    val presentation = e.presentation
    val component = e.inputEvent?.component as? JComponent ?: return
    val actionGroup = getActionGroup(e) ?: return
    val disposeCallback = { Toggleable.setSelected(presentation, false) }
    val popup = createPopup(actionGroup, e, disposeCallback)
    PopupImplUtil.setPopupToggleButton(popup, e.inputEvent.component)
    popup.setMinimumSize(JBDimension(MINIMAL_POPUP_WIDTH, 0))
    popup.showUnderneathOf(component)
  }

  open fun createPopup(actionGroup: ActionGroup,
                          e: AnActionEvent,
                          disposeCallback: () -> Unit) = JBPopupFactory.getInstance().createActionGroupPopup(
    null, actionGroup, e.dataContext, false, false, false, disposeCallback, 30, null)

  abstract fun getActionGroup(e: AnActionEvent): ActionGroup?
}

private class MoreRunToolbarActions : TogglePopupAction(
  IdeBundle.message("inline.actions.more.actions.text"), null, AllIcons.Actions.More
), DumbAware {
  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    val project = e.project ?: return null
    val selectedConfiguration = RunManager.getInstance(project).selectedConfiguration
    return createOtherRunnersSubgroup(selectedConfiguration, project)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

internal val excludeRunAndDebug: (Executor) -> Boolean = {
  // Cannot use DefaultDebugExecutor.EXECUTOR_ID because of module dependencies
  it.id != ToolWindowId.RUN && it.id != ToolWindowId.DEBUG
}

private fun createOtherRunnersSubgroup(runConfiguration: RunnerAndConfigurationSettings?, project: Project): ActionGroup? {
  if (runConfiguration != null) {
    return RunConfigurationsComboBoxAction.SelectConfigAction(runConfiguration, project, excludeRunAndDebug)
  }
  if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
    return RunConfigurationsComboBoxAction.RunCurrentFileAction(excludeRunAndDebug)
  }
  return ActionGroup.EMPTY_GROUP
}

private class RedesignedRunConfigurationSelector : TogglePopupAction(), CustomComponentAction, DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    if (e.inputEvent.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
      ActionManager.getInstance().getAction("editRunConfigurations").actionPerformed(e)
      return
    }
    super.actionPerformed(e)
  }

  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    val project = e.project ?: return null
    return createRunConfigurationsActionGroup(project, addHeader = false)
  }

  override fun createPopup(actionGroup: ActionGroup, e: AnActionEvent, disposeCallback: () -> Unit): ListPopup =
    RunConfigurationsActionGroupPopup(actionGroup, e.dataContext, disposeCallback)

  override fun update(e: AnActionEvent) {
    super.update(e)
    val action = ActionManager.getInstance().getAction("RunConfiguration")
    val runConfigAction = action as? RunConfigurationsComboBoxAction ?: return
    runConfigAction.update(e)
    e.presentation.setDescription(ExecutionBundle.messagePointer("choose.run.configuration.action.new.ui.button.description"))
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, JBUI.size(90, RUN_TOOLBAR_HEIGHT)){
      override fun getMargins(): Insets = JBInsets.create(0, 10)
      override fun iconTextSpace(): Int = JBUI.scale(6)
      override fun shallPaintDownArrow() = true
    }.also {
      it.foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
      it.setHorizontalTextAlignment(SwingConstants.LEFT)
    }
  }
}


private class RunToolbarSeparator(private val isCurrentConfigurationRunning: () -> Boolean) : JComponent() {
  override fun paint(g: Graphics) {
    super.paint(g)
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g2.color = getRunWidgetBackgroundColor(isCurrentConfigurationRunning())
      g2.fill(Rectangle(size))
      g2.color = JBUI.CurrentTheme.RunWidget.SEPARATOR
      g2.stroke = BasicStroke(JBUI.pixScale(this))
      g2.drawLine(0, JBUI.scale(5), 0, JBUI.scale(25))
    }
    finally {
      g2.dispose()
    }
  }

  override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(1), JBUI.scale(RUN_TOOLBAR_HEIGHT))
}

private fun getRunWidgetBackgroundColor(isRunning: Boolean): Color = if (isRunning)
  JBUI.CurrentTheme.RunWidget.RUNNING_BACKGROUND
else
  JBUI.CurrentTheme.RunWidget.BACKGROUND
