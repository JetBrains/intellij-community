// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.actions.StopAction
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.isOfSameType
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUiSizes
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.getHeaderBackgroundColor
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.lightThemeDarkHeaderDisableFilter
import com.intellij.openapi.wm.impl.headertoolbar.adjustIconForHeader
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.BadgeRectProvider
import com.intellij.ui.ColorUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.TextHoledIcon
import com.intellij.ui.icons.TextIcon
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import java.awt.*
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

const val CONFIGURATION_NAME_NON_TRIM_MAX_LENGTH = 25

private fun createRunActionToolbar(isCurrentConfigurationRunning: () -> Boolean): ActionToolbar {
  val toolbarId = "RunToolbarMainActionGroup"
  return ActionManager.getInstance().createActionToolbar(
    ActionPlaces.NEW_UI_RUN_TOOLBAR,
    ActionManager.getInstance().getAction(toolbarId) as ActionGroup,
    true
  ).apply {
    targetComponent = null
    setReservePlaceAutoPopupIcon(false)
    layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    if (this is ActionToolbarImpl) {
      isOpaque = false
      setMinimumButtonSize {
        JBUI.size(JBUI.CurrentTheme.RunWidget.actionButtonWidth(), JBUI.CurrentTheme.RunWidget.toolbarHeight())
      }
      setActionButtonBorder(2, JBUI.CurrentTheme.RunWidget.toolbarBorderHeight())
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
    val toolbar = createRunActionToolbar {
      presentation.getClientProperty(runToolbarDataKey) ?: false
    }
    toolbar.component.border = JBUI.Borders.emptyRight(16)
    return toolbar.component
  }

  override fun update(e: AnActionEvent) {
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

private class PreparedIcon(private val width: Int, private val height: Int, private val iconFn: () -> Icon) : RetrievableIcon {
  constructor(icon: Icon) : this(icon.iconWidth, icon.iconHeight, { icon })

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    iconFn().paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int = width

  override fun getIconHeight(): Int = height

  override fun retrieveIcon(): Icon = iconFn()

  override fun replaceBy(replacer: IconReplacer): Icon {
    return PreparedIcon(width, height) { replacer.replaceIcon(iconFn()) }
  }
}

private class RunWidgetButtonLook(private val isCurrentConfigurationRunning: () -> Boolean) : IdeaActionButtonLook() {
  override fun getStateBackground(component: JComponent, state: Int): Color? {
    val isStopButton = isStopButton(component)
    if (!isStopButton) {
      if (!buttonIsRunning(component) || !isCurrentConfigurationRunning()) {
        return getHeaderBackgroundColor(component, state)
      }
    }

    val color = if (isStopButton) JBUI.CurrentTheme.RunWidget.STOP_BACKGROUND
    else JBUI.CurrentTheme.RunWidget.RUNNING_BACKGROUND

    return when (state) {
      ActionButtonComponent.NORMAL -> color
      ActionButtonComponent.PUSHED -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.PRESSED_BACKGROUND, color)
      else -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.HOVER_BACKGROUND, color)
    }
  }

  override fun getDisabledIcon(icon: Icon): Icon {
    return IconLoader.getDisabledIcon(icon, lightThemeDarkHeaderDisableFilter)
  }

  override fun paintIcon(g: Graphics, actionButton: ActionButtonComponent, icon: Icon, x: Int, y: Int) {
    if (icon.iconWidth == 0 || icon.iconHeight == 0) {
      return
    }

    if (actionButton is ActionButton && actionButton.action is RedesignedRunConfigurationSelector) {
      super.paintIcon(g, actionButton, icon, x, y)
      return
    }

    var resultIcon = icon

    if (icon is LayeredIcon && icon.allLayers.size == 2) {
      val textIcon = icon.allLayers[1]
      if (textIcon is TextIcon) {
        val text = textIcon.text
        val provider = object : BadgeRectProvider() {
          override fun getTop() = 0.45
          override fun getLeft() = if (text.length == 1) 0.75 else 0.3
          override fun getBottom() = 1.2
          override fun getRight() = 1.2
        }
        resultIcon = TextHoledIcon(icon.allLayers[0], text, JBUIScale.scale(12.0f), JBUI.CurrentTheme.RunWidget.FOREGROUND, provider)
      }
    }

    if (resultIcon !is PreparedIcon) {
      val executionAction = (actionButton as? ActionButton)?.action is ExecutorRegistryImpl.ExecutorAction
      val iconWithBackground = executionAction && buttonIsRunning(actionButton) || isStopButton(actionButton)
      val resultColor = if (iconWithBackground) {
        JBUI.CurrentTheme.RunWidget.RUNNING_ICON_COLOR
      }
      else {
        if (executionAction) JBUI.CurrentTheme.RunWidget.RUN_ICON_COLOR
        else JBUI.CurrentTheme.RunWidget.ICON_COLOR
      }
      resultIcon = toStrokeIcon(resultIcon, resultColor)
    }

    super.paintIcon(g, actionButton, resultIcon, x, y)
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
    popup.setMinimumSize(JBDimension(MINIMAL_POPUP_WIDTH, 0))
    popup.showUnderneathOf(component)
  }

  open fun createPopup(actionGroup: ActionGroup,
                          e: AnActionEvent,
                          disposeCallback: () -> Unit) = JBPopupFactory.getInstance().createActionGroupPopup(
    null, actionGroup, e.dataContext, false, false, false, disposeCallback, 30, null)

  abstract fun getActionGroup(e: AnActionEvent): ActionGroup?
}

private class InactiveStopActionPlaceholder : DecorativeElement(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.icon = EmptyIcon.ICON_16
    if (StopAction.getActiveStoppableDescriptors(e.project).isEmpty()) {
      e.presentation.isEnabled = false
      e.presentation.isVisible = true
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }
}

private class MoreRunToolbarActions : TogglePopupAction(
  IdeBundle.message("inline.actions.more.actions.text"), null, AllIcons.Actions.More
), DumbAware {
  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    val project = e.project ?: return null
    val selectedConfiguration = RunManager.getInstance(project).selectedConfiguration
    val result = createOtherRunnersSubgroup(selectedConfiguration, project)
    addAdditionalActionsToRunConfigurationOptions(project, e, selectedConfiguration, result, true)
    return result
  }
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

internal val excludeRunAndDebug: (Executor) -> Boolean = {
  // Cannot use DefaultDebugExecutor.EXECUTOR_ID because of module dependencies
  it.id != ToolWindowId.RUN && it.id != ToolWindowId.DEBUG
}

private fun createOtherRunnersSubgroup(runConfiguration: RunnerAndConfigurationSettings?, project: Project): DefaultActionGroup {
  if (runConfiguration != null) {
    return RunConfigurationsComboBoxAction.SelectConfigAction(runConfiguration, project, excludeRunAndDebug)
  }
  if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
    return RunConfigurationsComboBoxAction.RunCurrentFileAction(excludeRunAndDebug)
  }
  return DefaultActionGroup()
}

internal fun addAdditionalActionsToRunConfigurationOptions(project: Project,
                                                           e: AnActionEvent,
                                                           selectedConfiguration: RunnerAndConfigurationSettings?,
                                                           targetGroup: DefaultActionGroup,
                                                           isWidget: Boolean) {
  val additionalActions = AdditionalRunningOptions.getInstance(project).getAdditionalActions(selectedConfiguration, isWidget)
  for (action in additionalActions.getChildren(e).reversed()) {
    targetGroup.add(action, Constraints.FIRST)
  }
}

private class RedesignedRunConfigurationSelector : TogglePopupAction(), CustomComponentAction, DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    if (e.inputEvent!!.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
      ActionManager.getInstance().getAction("editRunConfigurations").actionPerformed(e)
      return
    }
    super.actionPerformed(e)
  }

  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    val project = e.project ?: return null
    return createRunConfigurationsActionGroup(project, e)
  }

  override fun createPopup(actionGroup: ActionGroup, e: AnActionEvent, disposeCallback: () -> Unit): ListPopup =
    RunConfigurationsActionGroupPopup(actionGroup, e.dataContext, disposeCallback)

  override fun update(e: AnActionEvent) {
    super.update(e)
    val action = ActionManager.getInstance().getAction("RunConfiguration")
    val runConfigAction = action as? RunConfigurationsComboBoxAction ?: return
    runConfigAction.update(e)
    val icon = e.presentation.icon
    if (icon != null) {
      e.presentation.icon = adjustIconForHeader(icon)
    }
    val configurationName = e.project?.let { RunManager.getInstance(it) }?.selectedConfiguration?.name
    if (configurationName?.length?.let { it > CONFIGURATION_NAME_NON_TRIM_MAX_LENGTH } == true) {
      e.presentation.setDescription(ExecutionBundle.messagePointer("choose.run.configuration.action.new.ui.button.description.long",
                                                                   configurationName))
    }
    else {
      e.presentation.setDescription(ExecutionBundle.messagePointer("choose.run.configuration.action.new.ui.button.description"))
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, {
      JBUI.size(16, JBUI.CurrentTheme.RunWidget.toolbarHeight())
    }) {

      override fun getMargins(): Insets = JBInsets.create(0, 8)
      override fun iconTextSpace(): Int = ToolbarComboWidgetUiSizes.gapAfterLeftIcons
      override fun shallPaintDownArrow() = true
      override fun getDownArrowIcon(): Icon = PreparedIcon(super.getDownArrowIcon())

      override fun updateUI() {
        super.updateUI()
        updateFont()
      }

      fun updateFont() {
        font = JBUI.CurrentTheme.RunWidget.configurationSelectorFont()
      }

    }.also {
      it.foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
      it.setHorizontalTextAlignment(SwingConstants.LEFT)
      it.updateFont()
    }
  }
}

private fun buttonIsRunning(component: Any): Boolean =
  (component as? ActionButton)?.presentation?.getClientProperty(ExecutorRegistryImpl.EXECUTOR_ACTION_STATUS) ==
    ExecutorRegistryImpl.ExecutorActionStatus.RUNNING

private fun isStopButton(component: Any): Boolean =
  (component as? ActionButton)?.action is StopAction