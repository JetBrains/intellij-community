// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.DeferredIcon
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.InputEvent
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*

internal fun createRunToolbarWithoutStop(project: Project): ActionToolbar {
  val actionGroup = DefaultActionGroup()
  actionGroup.add(RunToolboxWrapper(project))
  actionGroup.addSeparator()
  actionGroup.add(StopWithDropDownAction())
  return ActionManager.getInstance().createActionToolbar(
    ActionPlaces.MAIN_TOOLBAR,
    actionGroup,
    true
  ).apply {
    targetComponent = null
    setReservePlaceAutoPopupIcon(false)
    layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    if (this is ActionToolbarImpl) {
      isOpaque = false
      setSeparatorCreator { Box.createHorizontalStrut(JBUIScale.scale(8)) }
      setMinimumButtonSize(JBUI.size(36, 30))
      setActionButtonBorder(Borders.empty())
    }
  }
}

private fun createRunActionToolbar(project: Project): ActionToolbar {
  val runners = CustomActionsSchema.getInstance().getCorrectedAction("RunnerActions") as ActionGroup
  val actionGroup = DefaultActionGroup()

  actionGroup.add(RunConfigurationSelector())
  actionGroup.addSeparator()

  val runActions = runners.getChildren(null).asList()
  actionGroup.add(runActions[0])
  actionGroup.add(runActions[1])
  actionGroup.add(OtherRunOptions())

  return ActionManager.getInstance().createActionToolbar(
    ActionPlaces.MAIN_TOOLBAR,
    actionGroup,
    true
  ).apply {
    targetComponent = null
    setReservePlaceAutoPopupIcon(false)
    layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    if (this is ActionToolbarImpl) {
      isOpaque = false
      setMinimumButtonSize(JBUI.size(36, 30))
      setActionButtonBorder(Borders.empty())
      setSeparatorCreator { createSeparator() }
      setCustomButtonLook(RunWidgetButtonLook(project))
    }
  }
}

private class RunToolboxWrapper(private val project: Project) : AnAction(), CustomComponentAction {
  private var remembered: RunnerAndConfigurationSettings? = null
  private var isRunning = false

  override fun actionPerformed(e: AnActionEvent): Unit = error("Should not be invoked")

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent = createRunActionToolbar(project).component

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val selectedConfiguration: RunnerAndConfigurationSettings? = RunManager.getInstance(project).selectedConfiguration
    val someRunning = isCurrentConfigurationRunning(project)
    if (remembered != selectedConfiguration || someRunning != isRunning) {
      component.repaint()
    }
    remembered = selectedConfiguration
    isRunning = someRunning
  }
}

private class RunWidgetButtonLook(private val project: Project) : IdeaActionButtonLook() {
  override fun getStateBackground(component: JComponent, state: Int): Color {

    val color = if (isCurrentConfigurationRunning(project))
      JBColor.namedColor("Green5", 0x599E5E)
    else
      JBColor.namedColor("Blue5",0x3369D6)

    return when (state) {
      ActionButtonComponent.NORMAL -> color
      ActionButtonComponent.PUSHED -> color.addAlpha(0.9)
      else -> color.addAlpha(0.9)
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
          val shape2 = Rectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), arc, height.toFloat())
          Area(shape1).also { it.add(Area(shape2)) }
        }
        component.parent?.components?.get(0) -> {
          val shape1 = RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), width.toFloat(), height.toFloat(), arc, arc)
          val shape2 = Rectangle2D.Float((rect.x + width).toFloat() - arc, rect.y.toFloat(), arc, height.toFloat())
          Area(shape1).also { it.add(Area(shape2)) }
        }
        else -> {
          Rectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), width.toFloat(), height.toFloat())
        }
      }

      g2.fill(shape)
    }
    finally {
      g2.dispose()
    }
  }


  override fun paintIcon(g: Graphics, actionButton: ActionButtonComponent, icon: Icon, x: Int, y: Int) {
    // TODO: need more magic about icons
    var targetIcon = icon
    if (targetIcon is DeferredIcon) {
      targetIcon = targetIcon.evaluate()

    } else {
      targetIcon = IconLoader.filterIcon(icon, { UIUtil.GrayFilter(100, 100, 100) }, null)
    }
    super.paintIcon(g, actionButton, targetIcon, x, y)
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}
  override fun getButtonArc(): JBValue = JBValue.Float(8f)
}


private abstract class TogglePopupAction : ToggleAction {

  constructor()

  constructor(@NlsActions.ActionText text: String?,
              @NlsActions.ActionDescription description: String?,
              icon: Icon?) : super(text, description, icon)

  private var selectedState: Boolean = false

  override fun isSelected(e: AnActionEvent): Boolean {
    return selectedState
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    selectedState = state
    if (!selectedState) return
    val component = e.inputEvent?.component as? JComponent ?: return
    val actionGroup = getActionGroup(e) ?: return
    val function = { selectedState = false }
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, actionGroup, e.dataContext, false, false, false, function, 30, null)
    popup.showUnderneathOf(component)
  }

  abstract fun getActionGroup(e: AnActionEvent): ActionGroup?
}

private class OtherRunOptions : TogglePopupAction(
  IdeBundle.message("show.options.menu"), IdeBundle.message("show.options.menu"), AllIcons.Actions.More
), DumbAware {
  override fun getActionGroup(e: AnActionEvent): ActionGroup {
    val runners = CustomActionsSchema.getInstance().getCorrectedAction("RunnerActions") as ActionGroup

    val runActions = runners.getChildren(e).asList()
    return DefaultActionGroup(runActions.subList(2, runActions.size))
  }
}

private class RunConfigurationSelector : TogglePopupAction(), CustomComponentAction, DumbAware {
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (e.inputEvent.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
      ActionManager.getInstance().getAction("editRunConfigurations").actionPerformed(e)
      return
    }
    super.setSelected(e, state)
  }

  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    val component = e.inputEvent?.component as? JComponent ?: return null
    val action = ActionManager.getInstance().getAction("RunConfiguration")
    val runConfigAction = action as? RunConfigurationsComboBoxAction ?: return null
    return runConfigAction.createPopupActionGroupOpen(component)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val action = ActionManager.getInstance().getAction("RunConfiguration")
    val runConfigAction = action as? RunConfigurationsComboBoxAction ?: return
    runConfigAction.update(e)
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, JBUI.size(90, 30)){
      override fun getMargins(): Insets = JBInsets.create(0, 10)
      override fun iconTextSpace(): Int = JBUI.scale(6)
    }.also {
      it.foreground = Color.WHITE
      it.setHorizontalTextAlignment(SwingConstants.LEFT)
    }
  }
}

private fun createSeparator(): JComponent {
  return JPanel().also {
    it.preferredSize = JBUI.size(1, 30)
    it.background = JBColor.namedColor("MainToolbar.background", CustomFrameDecorations.titlePaneBackground())
  }
}

private fun isCurrentConfigurationRunning(project: Project): Boolean {
  val selectedConfiguration: RunnerAndConfigurationSettings = RunManager.getInstance(project).selectedConfiguration ?: return false
  val runningDescriptors = ExecutionManagerImpl.getInstance(project).getRunningDescriptors { it === selectedConfiguration }
  return !runningDescriptors.isEmpty()
}

private fun Color.addAlpha(alpha: Double): Color {
  return JBColor.lazy { Color(red, green, blue, (255 * alpha).toInt()) }
}