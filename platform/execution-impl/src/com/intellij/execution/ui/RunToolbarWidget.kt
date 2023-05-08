// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.actions.RunConfigurationsComboBoxAction.SelectConfigAction
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.popup.KeepingPopupOpenAction
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.PopupState
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Predicate
import javax.swing.*
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonListener
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicGraphicsUtils
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.properties.Delegates

private const val RUN: String = DefaultRunExecutor.EXECUTOR_ID
private const val DEBUG: String = ToolWindowId.DEBUG

private val recentLimit: Int get() = AdvancedSettings.getInt("max.recent.run.configurations")

internal fun createRunConfigurationsActionGroup(project: Project, e: AnActionEvent): ActionGroup {
  val actions = DefaultActionGroup()
  val registry = ExecutorRegistry.getInstance()
  val runExecutor = registry.getExecutorById(RUN) ?: error("No '${RUN}' executor found")
  val debugExecutor = registry.getExecutorById(DEBUG) ?: error("No '${DEBUG}' executor found")
  val recents = RunConfigurationStartHistory.getInstance(project).history().take(max(recentLimit, 0))
  var shouldShowRecent: Boolean = recents.isNotEmpty()

  val shouldBeShown = { configuration: RunnerAndConfigurationSettings?, holdingFilter: Boolean ->
    when {
      !shouldShowRecent -> true
      holdingFilter && configuration != null -> !recents.contains(configuration)
      holdingFilter -> true
      else -> RunConfigurationStartHistory.getInstance(project).state.allConfigurationsExpanded
    }
  }

  val createActionFn: (RunnerAndConfigurationSettings) -> AnAction = { configuration ->
    createRunConfigurationWithInlines(runExecutor, debugExecutor, configuration, project, e) { shouldBeShown(configuration, it) }
  }
  val createFolderFn: (String) -> DefaultActionGroup = { folderName ->
    HideableDefaultActionGroup(folderName) { shouldBeShown(null, it) }
  }
  val filteringSubActions: (RunnerAndConfigurationSettings, String) -> AnAction = { configuration, folderName ->
    createRunConfigurationWithInlines(runExecutor, debugExecutor, configuration, project, e) { holdingFilter ->
      holdingFilter && !recents.contains(configuration)
    }.also {
      it.templatePresentation.putClientProperty(Presentation.PROP_VALUE, folderName)
    }
  }
  val allConfigurations = DefaultActionGroup()
  val allConfigurationsNumber = RunConfigurationsComboBoxAction.addRunConfigurations(allConfigurations, project, createActionFn, createFolderFn, filteringSubActions)

  if (shouldShowRecent && allConfigurationsNumber < recentLimit) {
    shouldShowRecent = false
  }

  if (shouldShowRecent) {
    actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.recent.separator.text")))
    for (conf in recents) {
      val actionGroupWithInlineActions = createRunConfigurationWithInlines(runExecutor, debugExecutor, conf, project, e)
      actions.add(actionGroupWithInlineActions)
    }
    actions.add(Separator.create())
  }
  if (shouldShowRecent) {
    actions.add(AllRunConfigurationsToggle(allConfigurationMessage(allConfigurationsNumber)))
  }
  actions.addAll(allConfigurations)

  if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
    actions.add(createCurrentFileWithInlineActions(runExecutor, debugExecutor, project))
  }
  actions.add(Separator.create())
  actions.add(ActionManager.getInstance().getAction("editRunConfigurations"))
  return actions
}

private fun allConfigurationMessage(number: Int): @Nls String {
  val textColor = ColorUtil.toHex(JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND)
  val message = ExecutionBundle.message("run.toolbar.widget.all.configurations", """<a style="color:#$textColor;">${number}</a>""")
  return "<html>$message</html>"
}

internal class RunConfigurationsActionGroupPopup(actionGroup: ActionGroup, dataContext: DataContext, disposeCallback: (() -> Unit)?) :
  PopupFactoryImpl.ActionGroupPopup(null, actionGroup, dataContext, false, false, true, false,
                                    disposeCallback, 30, null, null, PresentationFactory(), false) {

  init {
    (list as? JBList<*>)?.setExpandableItemsEnabled(false)
  }

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    val popup = super.createPopup(parent, step, parentValue)
    popup.minimumSize = JBDimension(MINIMAL_POPUP_WIDTH, 0)
    return popup
  }

  override fun shouldBeShowing(value: Any?): Boolean {
    if (!super.shouldBeShowing(value)) return false
    return if (value !is PopupFactoryImpl.ActionItem) true else shouldBeShowing(value.action, mySpeedSearch.isHoldingFilter)
  }


  fun shouldBeShowing(action: AnAction, holdingFilter: Boolean): Boolean {
    return if (action is HideableAction) return action.shouldBeShown(holdingFilter) else true
  }
}

private interface HideableAction {
  val shouldBeShown: (holdingFilter: Boolean) -> Boolean
}

private class HideableDefaultActionGroup(@NlsSafe name: String, override val shouldBeShown: (holdingFilter: Boolean) -> Boolean)
  : DefaultActionGroup({ name }, true), DumbAware, HideableAction

private class AllRunConfigurationsToggle(@NlsActions.ActionText text: String) : ToggleAction(text), KeepingPopupOpenAction, DumbAware {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = RunConfigurationStartHistory.getInstance(e.project!!).state.allConfigurationsExpanded

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    RunConfigurationStartHistory.getInstance(e.project!!).state.allConfigurationsExpanded = state

    val inputEvent = e.inputEvent ?: return
    val jList = inputEvent.source as? JList<*>
    val listPopupModel = jList?.model as? ListPopupModel<*> ?: return
    listPopupModel.refilter()
    PopupUtil.getPopupContainerFor(jList).pack(true, true)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = true
    e.presentation.icon = if (isSelected(e)) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight
  }
}

private fun createRunConfigurationWithInlines(runExecutor: Executor,
                                              debugExecutor: Executor,
                                              conf: RunnerAndConfigurationSettings,
                                              project: Project,
                                              e: AnActionEvent,
                                              shouldBeShown: (Boolean) -> Boolean = { true }
): SelectRunConfigurationWithInlineActions {
  val activeExecutor = getActiveExecutor(project, conf)
  val showRerunAndStopButtons = !conf.configuration.isAllowRunningInParallel && activeExecutor != null
  val inlineActions = if (showRerunAndStopButtons)
    listOf(
      ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(activeExecutor!!, conf, false),
      StopConfigurationInlineAction(activeExecutor, conf)
    )
  else
    listOf(
      ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(runExecutor, conf, false),
      ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(debugExecutor, conf, false)
    )

  val result = SelectRunConfigurationWithInlineActions(inlineActions, conf, project, shouldBeShown)
  if (showRerunAndStopButtons) {
    val extraExecutor = if (activeExecutor === runExecutor) debugExecutor else runExecutor
    val extraAction = ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(extraExecutor, conf, false)
    result.addAction(extraAction, Constraints.FIRST)
  }
  addAdditionalActionsToRunConfigurationOptions(project, e, conf, result, false)
  return result
}

private fun createCurrentFileWithInlineActions(runExecutor: Executor,
                                               debugExecutor: Executor,
                                               project: Project): AnAction {
  if (DumbService.isDumb(project)) {
    return RunConfigurationsComboBoxAction.RunCurrentFileAction { true }
  }
  val configs = getCurrentPsiFile(project)?.let { ExecutorRegistryImpl.ExecutorAction.getRunConfigsForCurrentFile(it, false) } ?: emptyList()
  val runRunningConfig = configs.firstOrNull { checkIfRunWithExecutor(it, runExecutor, project) }
  val debugRunningConfig = configs.firstOrNull { checkIfRunWithExecutor(it, debugExecutor, project) }
  val activeConfig = runRunningConfig ?: debugRunningConfig

  if (activeConfig == null || activeConfig.configuration.isAllowRunningInParallel) {
    return SelectCurrentFileWithInlineActions(listOf(
        ExecutorRegistryImpl.RunCurrentFileExecutorAction(runExecutor),
        ExecutorRegistryImpl.RunCurrentFileExecutorAction(debugExecutor))
    )
  }

  val inlineActions = mutableListOf<AnAction>()
  when {
    runRunningConfig != null -> {
      inlineActions.add(ExecutorRegistryImpl.RunCurrentFileExecutorAction(runExecutor))
      inlineActions.add(StopConfigurationInlineAction(runExecutor, runRunningConfig))
    }
    debugRunningConfig != null -> {
      inlineActions.add(ExecutorRegistryImpl.RunCurrentFileExecutorAction(debugExecutor))
      inlineActions.add(StopConfigurationInlineAction(debugExecutor, debugRunningConfig))
    }
    else -> {
      inlineActions.add(ExecutorRegistryImpl.RunCurrentFileExecutorAction(runExecutor))
      inlineActions.add(ExecutorRegistryImpl.RunCurrentFileExecutorAction(debugExecutor))
    }
  }

  val res = SelectCurrentFileWithInlineActions(inlineActions)
  if (runRunningConfig != null) res.addAction(ExecutorRegistryImpl.RunCurrentFileExecutorAction(debugExecutor), Constraints.FIRST)
  if (debugRunningConfig != null) res.addAction(ExecutorRegistryImpl.RunCurrentFileExecutorAction(runExecutor), Constraints.FIRST)

  return res
}

private fun checkIfRunWithExecutor(config: RunnerAndConfigurationSettings, executor: Executor, project: Project): Boolean {
  if (ProgramRunner.getRunner(executor.id, config.configuration) == null) return false
  return getActiveExecutor(project, config) === executor
}

private fun getCurrentPsiFile(project: Project): PsiFile? {
  return FileEditorManagerEx.Companion.getInstanceEx(project).currentFile?.findPsiFile(project)
}

private fun getActiveExecutor(project: Project, conf: RunnerAndConfigurationSettings): Executor? {
  val executionManager = ExecutionManagerImpl.getInstance(project)
  return executionManager.getRunningDescriptors { conf === it }.flatMap { executionManager.getExecutors(it) }.firstOrNull()
}

internal class SelectRunConfigurationWithInlineActions(
  private val actions: List<AnAction>,
  configuration: RunnerAndConfigurationSettings,
  project: Project,
  override val shouldBeShown: (holdingFilter: Boolean) -> Boolean
) : SelectConfigAction(configuration, project, excludeRunAndDebug), InlineActionsHolder, HideableAction {
  override fun getInlineActions(): List<AnAction> = actions
}

internal class SelectCurrentFileWithInlineActions(private val actions: List<AnAction>) :
  RunConfigurationsComboBoxAction.RunCurrentFileAction(excludeRunAndDebug), InlineActionsHolder {
  override fun getInlineActions(): List<AnAction> = actions
}


class StopWithDropDownAction : AnAction(), CustomComponentAction, DumbAware {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    stopAll(e)
  }

  override fun update(e: AnActionEvent) {
    if (!isContrastRunWidget) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val project = e.project ?: return
    val manager = ExecutionManagerImpl.getInstance(project)
    val running = getStoppableDescriptors(project).map { it.first }
    val activeProcesses = running.size
    e.presentation.putClientProperty(ACTIVE_PROCESSES, activeProcesses)
    e.presentation.isEnabled = activeProcesses > 0
    // presentations should be visible because it has to take some fixed space
    //e.presentation.isVisible = activeProcesses > 0
    e.presentation.icon = toStrokeIcon(AllIcons.Actions.Suspend, JBUI.CurrentTheme.RunWidget.FOREGROUND)
    if (activeProcesses == 1) {
      val first = running.first()
      getConfigurations(manager, first)
        ?.shortenName()
        ?.let {
          e.presentation.putClientProperty(SINGLE_RUNNING_NAME, it)
        }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return RunDropDownButton(icon = AllIcons.Actions.Suspend).apply {
      addActionListener {
        ActionUtil.performActionDumbAwareWithCallbacks(
          this@StopWithDropDownAction, AnActionEvent.createFromDataContext(
          place, presentation, DataManager.getInstance().getDataContext(this)))
      }
      isPaintEnable = false
      isCombined = true
    }.let { DynamicBorderWrapper(it) {
      JBUI.Borders.empty(JBUI.CurrentTheme.RunWidget.toolbarBorderHeight(), 6)
    } }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val processes = presentation.getClientProperty(ACTIVE_PROCESSES) ?: 0
    val wrapper = component as Wrapper
    val button = wrapper.targetComponent as RunDropDownButton
    RunButtonColors.RED.updateColors(button)
    button.isPaintEnable = processes > 0
    button.isEnabled = presentation.isEnabled
    button.dropDownPopup = if (processes == 1) null else { context ->
      context.getData(PlatformDataKeys.PROJECT)?. let { createStopPopup(context, it) }
    }
    button.toolTipText = when {
      processes == 1 -> ExecutionBundle.message("run.toolbar.widget.stop.description", presentation.getClientProperty(SINGLE_RUNNING_NAME))
      processes > 1 -> ExecutionBundle.message("run.toolbar.widget.stop.multiple.description")
      else -> null
    }
    button.icon = presentation.icon
  }

  companion object {
    private val ACTIVE_PROCESSES: Key<Int> = Key.create("ACTIVE_PROCESSES")
    private val SINGLE_RUNNING_NAME: Key<String> = Key.create("SINGLE_RUNNING_NAME")
  }
}

private fun stopAll(e: AnActionEvent) {
  val project = e.project ?: return
  getStoppableDescriptors(project).map { it.first }
    .forEach { descr ->
      ExecutionManagerImpl.stopProcess(descr)
    }
}

fun createStopPopup(context: DataContext, project: Project): JBPopup {
  val group = DefaultActionGroup()
  val descriptorsByEnv = getStoppableDescriptors(project)
  descriptorsByEnv.forEach { (descr, settings) ->
    val name = settings?.shortenName() ?: descr.displayName
    group.add(DumbAwareAction.create(ExecutionBundle.message("stop.item.new.ui.popup", name)) {
      ExecutionManagerImpl.stopProcess(descr)
    })
  }
  group.addSeparator()
  val textColor = ColorUtil.toHex(JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND)
  val message = ExecutionBundle.message("stop.all.new.ui.popup", """<a style="color:#$textColor;">${descriptorsByEnv.size}</a>""")
  group.add(DumbAwareAction.create("""<html>$message</html>""") {
    stopAll(it)
  }.also {
    it.copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM))
  })
  return JBPopupFactory.getInstance().createActionGroupPopup(
    null,
    group,
    context,
    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
    true,
    ActionPlaces.getPopupPlace(ActionPlaces.MAIN_TOOLBAR)
  )
}

fun runCounterToString(e: AnActionEvent, stopCount: Int): String =
  if (stopCount > 9 && e.place == ActionPlaces.NEW_UI_RUN_TOOLBAR) {
    "9+"
  }
  else {
    stopCount.toString()
  }

private class StopConfigurationInlineAction(val executor: Executor, val settings: RunnerAndConfigurationSettings) : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    getRunningDescriptor(project)?.let { ExecutionManagerImpl.stopProcess(it) }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation

    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    presentation.text = ExecutionBundle.message("run.toolbar.widget.stop.description", settings.shortenName())
    presentation.icon = AllIcons.Actions.Suspend

    presentation.isEnabledAndVisible = getRunningDescriptor(project) != null
  }

  private fun getRunningDescriptor(project: Project): RunContentDescriptor? {
    val executionManager = ExecutionManagerImpl.getInstance(project)
    val runningDescriptors = executionManager.getRunningDescriptors { settings === it }
    for (desc in runningDescriptors) {
      if (executionManager.getExecutors(desc).contains(executor)) return desc
    }

    return null
  }
}

private enum class RunButtonColors {
  RED {
    override fun updateColors(button: RunDropDownButton) {
      button.foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
      button.background = getColor("RunWidget.StopButton.background") { ColorUtil.fromHex("#EB7171") }
      button.hoverBackground = getColor("RunWidget.StopButton.leftHoverBackground") { ColorUtil.fromHex("#E35252") }
      button.pressedBackground = getColor("RunWidget.StopButton.leftPressedBackground") { ColorUtil.fromHex("#C94F4F") }
    }
  };

  abstract fun updateColors(button: RunDropDownButton)

  companion object {
    private fun getColor(propertyName: String, defaultColor: () -> Color): JBColor {
      return JBColor.lazy {
        UIManager.getColor(propertyName) ?: let {
          defaultColor().also {
            UIManager.put(propertyName, it)
          }
        }
      }
    }
  }
}

private open class RunDropDownButton(
  @Nls text: String? = null,
  icon: Icon? = null
) : JButton(text, icon) {

  var dropDownPopup: ((DataContext) -> JBPopup?)? by Delegates.observable(null) { prop, oldValue, newValue ->
    firePropertyChange(prop.name, oldValue, newValue)
    if (oldValue != newValue) {
      revalidate()
      repaint()
    }
  }
  var separatorColor: Color? = null
  var hoverBackground: Color? = null
  var pressedBackground: Color? = null
  var roundSize: Int = 8

  /**
   * When false the button isn't painted but still takes space in the UI.
   */
  var isPaintEnable by Delegates.observable(true) { prop, oldValue, newValue ->
    firePropertyChange(prop.name, oldValue, newValue)
    if (oldValue != newValue) {
      repaint()
    }
  }

  /**
   * When true there's no left/right parts.
   *
   * If [dropDownPopup] is set then it is shown instead of firing [actionListener].
   */
  var isCombined by Delegates.observable(false) { prop, oldValue, newValue ->
    firePropertyChange(prop.name, oldValue, newValue)
    if (oldValue != newValue) {
      revalidate()
      repaint()
    }
  }

  override fun setUI(ui: ButtonUI?) {
    super.setUI(RunDropDownButtonUI())
  }
}

private class RunDropDownButtonUI : BasicButtonUI() {

  private var point: Point? = null
  private val viewRect = Rectangle()
  private val textRect = Rectangle()
  private val iconRect = Rectangle()

  override fun installDefaults(b: AbstractButton?) {
    super.installDefaults(b)
    b as RunDropDownButton
    b.border = JBUI.Borders.empty(0, 7)
    b.isOpaque = false
    b.foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
    b.background = ColorUtil.fromHex("#3574F0")
    b.hoverBackground = ColorUtil.fromHex("#3369D6")
    b.pressedBackground = ColorUtil.fromHex("#315FBD")
    b.horizontalAlignment = SwingConstants.LEFT
    b.margin = JBInsets.emptyInsets()
  }

  override fun createButtonListener(b: AbstractButton?): BasicButtonListener {
    return MyHoverListener(b as RunDropDownButton)
  }

  override fun getPreferredSize(c: JComponent?): Dimension? {
    c as RunDropDownButton
    val prefSize = BasicGraphicsUtils.getPreferredButtonSize(c, c.iconTextGap)
    return prefSize?.apply {
      width = maxOf(width, if (c.isCombined) 0 else 72)
      height = JBUIScale.scale(JBUI.CurrentTheme.RunWidget.toolbarHeight())
      /**
       * If combined view is enabled the button should not draw a separate line
       * and reserve a place if dropdown is not enabled. Therefore, add only a half
       * of arrow icon width (the same as height, 'cause it's square).
       */
      if (c.isCombined) {
        width += height / 2
      }
      /**
       * If it is not a combined view then check if dropdown required and add its width (= height).
       */
      else if (c.dropDownPopup != null) {
        width += height
      }
    }
  }

  override fun paint(g: Graphics, c: JComponent) {
    val b = c as RunDropDownButton
    if (!b.isPaintEnable) return
    val bounds = g.clipBounds
    val popupWidth = if (b.dropDownPopup != null) bounds.height else 0

    val g2d = g.create(bounds.x, bounds.y, bounds.width, bounds.height) as Graphics2D

    val text = layout(b, b.getFontMetrics(g2d.font), b.width - popupWidth, b.height)

    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

      g2d.color = c.background
      val buttonRectangle: Shape = RoundRectangle2D.Double(
        bounds.x.toDouble(),
        bounds.y.toDouble(),
        bounds.width.toDouble() - if (b.isCombined && b.dropDownPopup == null) bounds.height / 2 else 0,
        bounds.height.toDouble(),
        JBUIScale.scale(c.roundSize).toDouble(),
        JBUIScale.scale(c.roundSize).toDouble()
      )

      g2d.fill(buttonRectangle)

      val popupBounds = Rectangle(viewRect.x + viewRect.width + c.insets.right, bounds.y, bounds.height, bounds.height)

      point?.let { p ->
        g2d.color = if (b.model.isArmed && b.model.isPressed) c.pressedBackground else c.hoverBackground
        val highlighted = Area(buttonRectangle)
        if (!b.isCombined) {
          if (popupBounds.contains(p)) {
            highlighted.subtract(Area(Rectangle2D.Double(
              bounds.x.toDouble(),
              bounds.y.toDouble(),
              bounds.width.toDouble() - popupBounds.width,
              bounds.height.toDouble()
            )))
          }
          else {
            highlighted.subtract(Area(Rectangle2D.Double(
              popupBounds.x.toDouble(),
              popupBounds.y.toDouble(),
              popupBounds.width.toDouble(),
              popupBounds.height.toDouble()
            )))
          }
        }
        g2d.fill(highlighted)
      }

      if (popupWidth > 0) {
        g2d.color = b.separatorColor
        if (!b.isCombined) {
          val gap = popupBounds.height / 5
          g2d.drawLine(popupBounds.x, popupBounds.y + gap, popupBounds.x, popupBounds.y + popupBounds.height - gap)
        }
        g2d.color = b.foreground
        paintArrow(c, g2d, popupBounds)
      }

      paintIcon(g2d, c, iconRect)
      paintText(g2d, c, textRect, text)
    }
    finally {
      g2d.dispose()
    }
  }

  private fun paintArrow(c: Component, g: Graphics2D, r: Rectangle) {
    JBInsets.removeFrom(r, JBUI.insets(1, 0, 1, 1))

    val tW = JBUIScale.scale(8f)
    val tH = JBUIScale.scale(tW / 2)

    val xU = (r.getWidth() - tW) / 2 + r.x
    val yU = (r.getHeight() - tH) / 2 + r.y

    val leftLine = Line2D.Double(xU, yU, xU + tW / 2f, yU + tH)
    val rightLine = Line2D.Double(xU + tW / 2f, yU + tH, xU + tW, yU)

    g.color = c.foreground
    g.stroke = BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER)
    g.draw(leftLine)
    g.draw(rightLine)
  }

  private fun layout(b: AbstractButton, fm: FontMetrics, width: Int, height: Int): String? {
    val i = b.insets
    viewRect.x = i.left
    viewRect.y = i.top
    viewRect.width = width - (i.right + viewRect.x)
    viewRect.height = height - (i.bottom + viewRect.y)
    textRect.height = 0
    textRect.width = textRect.height
    textRect.y = textRect.width
    textRect.x = textRect.y
    iconRect.height = 0
    iconRect.width = iconRect.height
    iconRect.y = iconRect.width
    iconRect.x = iconRect.y

    // layout the text and icon
    return SwingUtilities.layoutCompoundLabel(
      b, fm, b.text, b.icon,
      b.verticalAlignment, b.horizontalAlignment,
      b.verticalTextPosition, b.horizontalTextPosition,
      viewRect, iconRect, textRect,
      if (b.text == null) 0 else b.iconTextGap)
  }

  private inner class MyHoverListener(val button: RunDropDownButton) : BasicButtonListener(button) {
    private val popupState = PopupState.forPopup()

    override fun mouseEntered(e: MouseEvent) {
      if (popupState.isShowing) return
      super.mouseEntered(e)
      point = e.point
      e.component.repaint()
    }

    override fun mouseExited(e: MouseEvent) {
      if (popupState.isShowing) return
      super.mouseExited(e)
      point = null
      e.component.repaint()
    }

    override fun mouseMoved(e: MouseEvent) {
      if (popupState.isShowing) return
      super.mouseMoved(e)
      point = e.point
      e.component.repaint()
    }

    override fun mousePressed(e: MouseEvent) {
      if (popupState.isShowing || popupState.isRecentlyHidden) return
      val b = e.source as? RunDropDownButton
      if (b?.dropDownPopup != null && b.isEnabled) {
        if (b.isCombined || b.width - b.height < e.x) {
          b.dropDownPopup?.invoke(DataManager.getInstance().getDataContext(e.component))
            ?.also {
              popupState.prepareToShow(it)
              it.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                  point = null
                  button.repaint()
                }
              })
            }
            ?.showUnderneathOf(e.component)
          return
        }
      }

      super.mousePressed(e)
    }
  }
}

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class RunStatusHistory {
  private val history = WeakHashMap<RunnerAndConfigurationSettings, MutableList<RunElement>>()

  class RunElement(
    val executorId: String,
    var state: RunState
  )

  private val lock = ReentrantReadWriteLock()


  fun changeState(setting: RunnerAndConfigurationSettings, executorId: String, state: RunState) = lock.write {
    val runElements = history.computeIfAbsent(setting) {
      ArrayList(5)
    }
    runElements.firstOrNull { it.executorId == executorId }?.let { it.state = state } ?: runElements.add(RunElement(executorId, state))
  }

  fun firstOrNull(setting: RunnerAndConfigurationSettings?, predicate: Predicate<RunElement> = Predicate { true }): RunElement? = lock.read {
    setting ?: return null

    return history[setting]?.firstOrNull { predicate.test(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunStatusHistory = project.service()
  }
}

@Service(Service.Level.PROJECT)
@State(name = "RunConfigurationStartHistory", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@ApiStatus.Internal
class RunConfigurationStartHistory(private val project: Project) : PersistentStateComponent<RunConfigurationStartHistory.State> {
  class State {
    @XCollection(style = XCollection.Style.v2)
    @OptionTag("element")
    var history: MutableSet<Element>

    var allConfigurationsExpanded: Boolean

    constructor() {
      history = mutableSetOf()
      allConfigurationsExpanded = false
    }

    internal constructor(history: MutableSet<Element>, allConfigurationsExpanded: Boolean) {
      this.history = history
      this.allConfigurationsExpanded = allConfigurationsExpanded
    }
  }

  @Tag("element")
  data class Element(
    @Attribute
    var setting: String? = "",
  )

  fun history(): List<RunnerAndConfigurationSettings> {
    val settings = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }
    return _state.history.mapNotNull { settings[it.setting] }
  }

  fun register(setting: RunnerAndConfigurationSettings) {
    _state = State(_state.history.take(max(5, recentLimit*2)).toMutableList().apply {
      add(0, Element(setting.uniqueID))
    }.toMutableSet(), _state.allConfigurationsExpanded)
  }

  private var _state = State()

  override fun getState() = _state

  override fun loadState(state: State) {
    _state = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunConfigurationStartHistory = project.service()
  }
}

private class ExecutionReasonableHistoryManager : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.messageBus.connect(project).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
        onAnyChange(executorId, env, RunState.SCHEDULED)
      }

      override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        onAnyChange(executorId, env, RunState.NOT_STARTED)
      }

      override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        onAnyChange(executorId, env, RunState.STARTED)
      }

      override fun processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        onAnyChange(executorId, env, RunState.TERMINATING)
      }

      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        onAnyChange(executorId, env, RunState.TERMINATED)
      }

      private fun onAnyChange(executorId: String, env: ExecutionEnvironment, reason: RunState) {
        getPersistedConfiguration(env.runnerAndConfigurationSettings)?.let { conf ->
          RunStatusHistory.getInstance(env.project).changeState(conf, executorId, reason)
          ActivityTracker.getInstance().inc() // Not sure is it needed at all
        }
      }
    })
  }
}

enum class RunState {
  UNDEFINED,
  SCHEDULED,
  NOT_STARTED,
  STARTED,
  TERMINATING,
  TERMINATED;
}

private fun getPersistedConfiguration(configuration: RunnerAndConfigurationSettings?): RunnerAndConfigurationSettings? {
  var conf: RunProfile = (configuration ?: return null).configuration
  conf = ExecutionManagerImpl.getDelegatedRunProfile(conf) ?: conf
  return RunManager.getInstance(configuration.configuration.project).allSettings.find { it.configuration == conf }
}

private fun getConfigurations(manager: ExecutionManagerImpl, descriptor: RunContentDescriptor): RunnerAndConfigurationSettings? {
  return manager.getConfigurations(descriptor).firstOrNull()?.let(::getPersistedConfiguration)
}

@Nls
private fun RunnerAndConfigurationSettings.shortenName() = Executor.shortenNameIfNeeded(name)
