// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.actions.RunConfigurationsComboBoxAction.SelectConfigAction
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.ui.customization.CustomizeActionGroupPanel
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.popup.KeepingPopupOpenAction
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.PopupState
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.annotations.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.*
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonListener
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicGraphicsUtils
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.Delegates

private const val RUN_TOOLBAR_WIDGET_GROUP = "RunToolbarWidgetCustomizableActionGroup"

private val CONF: Key<RunnerAndConfigurationSettings> = Key.create("RUNNER_AND_CONFIGURATION_SETTINGS")
private val COLOR: Key<RunButtonColors> = Key.create("RUN_BUTTON_COLOR")
private val EXECUTOR_ID: Key<String> = Key.create("RUN_WIDGET_EXECUTOR_ID")

private const val RUN: String = DefaultRunExecutor.EXECUTOR_ID
private const val DEBUG: String = ToolWindowId.DEBUG
private const val PROFILER: String = "Profiler"

internal class RunToolbarWidgetCustomizableActionGroupProvider : CustomizableActionGroupProvider() {
  override fun registerGroups(registrar: CustomizableActionGroupRegistrar?) {
    if (ExperimentalUI.isNewUI()) {
      registrar?.addCustomizableActionGroup(RUN_TOOLBAR_WIDGET_GROUP, ExecutionBundle.message("run.toolbar.widget.customizable.group.name"))
    }
  }
}

internal class RunWithDropDownAction : AnAction(AllIcons.Actions.Execute), CustomComponentAction, DumbAware {
  private val spinningIcon = SpinningProgressIcon()

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    if (!e.presentation.isEnabled) return
    val conf = e.presentation.getClientProperty(CONF)
    if (conf != null) {
      val executor = getExecutorByIdOrDefault(e.presentation.getClientProperty(EXECUTOR_ID)!!)
      RunToolbarWidgetRunAction(executor, false) { conf }.actionPerformed(e)
    }
    else {
      ActionManager.getInstance().getAction("editRunConfigurations").actionPerformed(e)
    }
  }

  override fun update(e: AnActionEvent) {
    if (Registry.`is`("ide.experimental.ui.redesigned.run.widget")) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val project = e.project
    val runManager = project?.serviceIfCreated<RunManager>()
    if (runManager == null) {
      e.presentation.icon = spinningIcon
      e.presentation.text = ExecutionBundle.message("run.toolbar.widget.loading.text")
      e.presentation.isEnabled = false
      return
    }

    val selectedConfiguration = runManager.selectedConfiguration
    val history = RunStatusHistory.getInstance(project)
    val run = history.firstOrNull(selectedConfiguration) { it.state.isRunningState() } ?: history.firstOrNull(selectedConfiguration)
    val isLoading = run?.state?.isBusyState() == true
    val lastExecutorId = run?.executorId ?: DefaultRunExecutor.EXECUTOR_ID
    e.presentation.putClientProperty(CONF, selectedConfiguration)
    e.presentation.putClientProperty(EXECUTOR_ID, lastExecutorId)
    if (selectedConfiguration != null) {
      val isRunning = run?.state == RunState.STARTED || run?.state == RunState.TERMINATING
      val canRestart = isRunning && !selectedConfiguration.configuration.isAllowRunningInParallel
      e.presentation.putClientProperty(COLOR, if (isRunning) RunButtonColors.GREEN else RunButtonColors.BLUE)
      e.presentation.icon = if (isLoading) spinningIcon else iconFor(lastExecutorId, canRestart)
      e.presentation.text = selectedConfiguration.shortenName()
      e.presentation.description = RunToolbarWidgetRunAction.reword(getExecutorByIdOrDefault(lastExecutorId), canRestart, selectedConfiguration.shortenName())
    } else {
      e.presentation.putClientProperty(COLOR, RunButtonColors.BLUE)
      e.presentation.icon = iconFor(RUN, false)
      e.presentation.text = ExecutionBundle.message("run.toolbar.widget.run.text")
      e.presentation.description = ExecutionBundle.message("run.toolbar.widget.run.description")
    }
    e.presentation.isEnabled = !isLoading
  }

  private fun getExecutorByIdOrDefault(executorId: String): Executor {
    return ExecutorRegistryImpl.getInstance().getExecutorById(executorId)
           ?: ExecutorRegistryImpl.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
           ?: error("Run executor is not found")
  }

  private fun iconFor(executorId: String, needRerunIcon: Boolean): Icon {
    val icon = getExecutorByIdOrDefault(executorId).let { if (needRerunIcon) it.rerunIcon else it.icon }
    return IconUtil.toStrokeIcon(icon, JBUI.CurrentTheme.RunWidget.FOREGROUND)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return RunDropDownButton(presentation.text, presentation.icon).apply {
      addActionListener {
        val anActionEvent = AnActionEvent.createFromDataContext(place, presentation, DataManager.getInstance().getDataContext(this))
        if (it.modifiers and ActionEvent.SHIFT_MASK != 0) {
          ActionUtil.performActionDumbAwareWithCallbacks(ActionManager.getInstance().getAction("editRunConfigurations"), anActionEvent)
        }
        else if (it.modifiers  and ActionEvent.ALT_MASK != 0) {
          CustomizeActionGroupPanel.showDialog(RUN_TOOLBAR_WIDGET_GROUP, listOf(IdeActions.GROUP_NAVBAR_TOOLBAR), ExecutionBundle.message("run.toolbar.widget.customizable.group.dialog.title"))
            ?.let { result ->
              CustomizationUtil.updateActionGroup(result, RUN_TOOLBAR_WIDGET_GROUP)
              CustomActionsSchema.setCustomizationSchemaForCurrentProjects()
            }
        }
        else {
          ActionUtil.performActionDumbAwareWithCallbacks(this@RunWithDropDownAction, anActionEvent)
        }
      }
    }.let { Wrapper(it).apply { border = JBUI.Borders.empty(7,6) } }
  }

  override fun updateCustomComponent(wrapper: JComponent, presentation: Presentation) {
    val component = (wrapper as Wrapper).targetComponent as RunDropDownButton
    component.text = presentation.text?.let(::shorten)
    component.icon = presentation.icon.also { currentIcon ->
      if (spinningIcon === currentIcon) {
        spinningIcon.setIconColor(component.foreground)
      }
    }
    presentation.getClientProperty(COLOR)?.updateColors(component)
    if (presentation.getClientProperty(CONF) == null) {
      component.dropDownPopup = null
    } else {
      component.dropDownPopup = { context -> createRunConfigurationPopup(context, context.getData(PlatformDataKeys.PROJECT)!!) }
    }
    component.toolTipText = presentation.description
    component.invalidate()
  }
}

internal fun createRunConfigurationsActionGroup(project: Project, addHeader: Boolean = true): ActionGroup {
  val actions = DefaultActionGroup()
  val registry = ExecutorRegistry.getInstance()
  val runExecutor = registry.getExecutorById(RUN) ?: error("No '${RUN}' executor found")
  val debugExecutor = registry.getExecutorById(DEBUG) ?: error("No '${DEBUG}' executor found")
  if (addHeader) {
    val profilerExecutor: ExecutorGroup<*>? = registry.getExecutorById(PROFILER) as? ExecutorGroup<*>
    actions.add(RunToolbarWidgetRunAction(runExecutor) { RunManager.getInstance(it).selectedConfiguration })
    actions.add(RunToolbarWidgetRunAction(debugExecutor) { RunManager.getInstance(it).selectedConfiguration })
    if (profilerExecutor != null) {
      actions.add(profilerExecutor.createExecutorActionGroup { RunManager.getInstance(it).selectedConfiguration })
    }
  }
  actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.recent.separator.text")))
  RunConfigurationStartHistory.getInstance(project).history().forEach { conf ->
    val actionGroupWithInlineActions = createRunConfigurationWithInlines(runExecutor, debugExecutor, conf, project)
    actions.add(actionGroupWithInlineActions)
  }
  actions.add(Separator.create())
  if (Registry.`is`("ide.experimental.ui.redesigned.run.popup")) {
    val allRunConfigurationsToggle = AllRunConfigurationsToggle()
    actions.add(allRunConfigurationsToggle)

    val createActionFn: (RunnerAndConfigurationSettings) -> AnAction = { configuration ->
      createRunConfigurationWithInlines(runExecutor, debugExecutor, configuration, project) {
        allRunConfigurationsToggle.selected
      }
    }
    val createFolderFn: (String) -> DefaultActionGroup = { folderName ->
      HideableDefaultActionGroup(folderName) {
        allRunConfigurationsToggle.selected
      }
    }
    RunConfigurationsComboBoxAction.addRunConfigurations(actions, project, createActionFn, createFolderFn)
  }
  else {
    actions.add(DelegateAction({ ExecutionBundle.message("run.toolbar.widget.all.configurations") },
                               ActionManager.getInstance().getAction("ChooseRunConfiguration")))
  }

  if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
    actions.add(SelectCurrentFileWithInlineActions(listOf(
      ExecutorRegistryImpl.RunCurrentFileExecutorAction(runExecutor),
      ExecutorRegistryImpl.RunCurrentFileExecutorAction(debugExecutor))))
  }
  actions.add(Separator.create())
  actions.add(ActionManager.getInstance().getAction("editRunConfigurations"))
  return actions
}

internal class RunConfigurationsActionGroupPopup(actionGroup: ActionGroup, dataContext: DataContext, disposeCallback: (() -> Unit)?) :
  PopupFactoryImpl.ActionGroupPopup(null, actionGroup, dataContext, false, false, true, false,
                                    disposeCallback, 30, null, null) {

  init {
    (list as? JBList<*>)?.setExpandableItemsEnabled(false)
  }

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    val popup = super.createPopup(parent, step, parentValue)
    popup.setMinimumSize(JBDimension(MINIMAL_POPUP_WIDTH, 0))
    return popup
  }

  override fun shouldBeShowing(value: Any?): Boolean {
    if (!super.shouldBeShowing(value)) return false
    return if (value !is PopupFactoryImpl.ActionItem) true else shouldBeShowing(value.action)
  }


  fun shouldBeShowing(action: AnAction): Boolean {
    return if (action is HideableAction) return action.shouldBeShown() else true
  }
}

private interface HideableAction {
  val shouldBeShown: () -> Boolean
}

private class HideableDefaultActionGroup(@NlsSafe name: String, override val shouldBeShown: () -> Boolean)
  : DefaultActionGroup({ name }, true), DumbAware, HideableAction

private class AllRunConfigurationsToggle : ToggleAction(
  ExecutionBundle.message("run.toolbar.widget.all.configurations")), KeepingPopupOpenAction, DumbAware {
  var selected = false

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = selected

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    selected = state

    val inputEvent = e.inputEvent ?: return
    val jList = inputEvent.source as? JList<*>
    val listPopupModel = jList?.model as? ListPopupModel<*> ?: return
    listPopupModel.refilter()
    PopupUtil.getPopupContainerFor(jList).pack(true, true)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = true
    e.presentation.icon = if (selected) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight
  }
}

private fun createRunConfigurationWithInlines(runExecutor: Executor,
                                              debugExecutor: Executor,
                                              conf: RunnerAndConfigurationSettings,
                                              project: Project,
                                              shouldBeShown: () -> Boolean = { true }): SelectRunConfigurationWithInlineActions {
  val inlineActions = mutableListOf<AnAction>()
  inlineActions.add(RunToolbarWidgetRunAction(runExecutor) { conf })
  inlineActions.add(RunToolbarWidgetRunAction(debugExecutor) { conf })

  return SelectRunConfigurationWithInlineActions(inlineActions, conf, project, shouldBeShown)
}

private fun createRunConfigurationPopup(context: DataContext, project: Project): JBPopup {
  val actions = createRunConfigurationsActionGroup(project)
  return JBPopupFactory.getInstance().createActionGroupPopup(
    null,
    actions,
    context,
    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
    true,
    ActionPlaces.getPopupPlace(ActionPlaces.MAIN_TOOLBAR)
  ).apply { disableExpandableItems(this) }
}

private fun disableExpandableItems(popup: ListPopup) {
  val list = (popup as? ListPopupImpl)?.list
  (list as? JBList<*>)?.setExpandableItemsEnabled(false)
}

private fun ExecutorGroup<*>.createExecutorActionGroup(conf: (Project) -> RunnerAndConfigurationSettings?) = DefaultActionGroup().apply {
  templatePresentation.text = actionName
  isPopup = true

  childExecutors().forEach { executor ->
    add(RunToolbarWidgetRunAction(executor, hideIfDisable = true, conf))
  }
}

private class DelegateAction(val string: Supplier<@Nls String>, delegate: AnAction) : AnActionWrapper(delegate) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = string.get()
  }
}

internal class SelectRunConfigurationWithInlineActions(
  private val actions: List<AnAction>,
  configuration: RunnerAndConfigurationSettings,
  project: Project,
  override val shouldBeShown: () -> Boolean
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
    ExecutionManagerImpl.getInstance(e.project ?: return)
      .getRunningDescriptors { true }
      .forEach { descr ->
        ExecutionManagerImpl.stopProcess(descr)
      }
  }

  override fun update(e: AnActionEvent) {
    val manger = ExecutionManagerImpl.getInstance(e.project ?: return)
    val running = manger.getRunningDescriptors { true }
    val activeProcesses = running.size
    e.presentation.putClientProperty(ACTIVE_PROCESSES, activeProcesses)
    e.presentation.isEnabled = activeProcesses > 0
    // presentations should be visible because it has to take some fixed space
    //e.presentation.isVisible = activeProcesses > 0
    e.presentation.icon = IconUtil.toStrokeIcon(AllIcons.Actions.Suspend, JBUI.CurrentTheme.RunWidget.FOREGROUND)
    if (activeProcesses == 1) {
      val first = running.first()
      getConfigurations(manger, first)
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
    }.let { Wrapper(it).apply {
      border = JBUI.Borders.empty(if (Registry.`is`("ide.experimental.ui.redesigned.run.widget")) RUN_TOOLBAR_BORDER_HEIGHT else 7,6)
    } }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val processes = presentation.getClientProperty(ACTIVE_PROCESSES) ?: 0
    val wrapper = component as Wrapper
    val button = wrapper.targetComponent as RunDropDownButton
    RunButtonColors.RED.updateColors(button)
    button.isPaintEnable = processes > 0
    button.isEnabled = presentation.isEnabled
    button.dropDownPopup = if (processes == 1) null else { context -> createPopup(context, context.getData(PlatformDataKeys.PROJECT)!!) }
    button.toolTipText = when {
      processes == 1 -> ExecutionBundle.message("run.toolbar.widget.stop.description", presentation.getClientProperty(SINGLE_RUNNING_NAME))
      processes > 1 -> ExecutionBundle.message("run.toolbar.widget.stop.multiple.description")
      else -> null
    }
    button.icon = presentation.icon
  }

  private fun createPopup(context: DataContext, project: Project): JBPopup {
    val group = DefaultActionGroup()
    val manager = ExecutionManagerImpl.getInstance(project)
    val running = manager.getRunningDescriptors { true }.asReversed()
    running.forEach { descr ->
      val name = getConfigurations(manager, descr)?.shortenName() ?: descr.displayName
      group.add(DumbAwareAction.create(name) {
        ExecutionManagerImpl.stopProcess(descr)
      })
    }
    group.addSeparator()
    group.add(DumbAwareAction.create(ExecutionBundle.message("stop.all", KeymapUtil.getFirstKeyboardShortcutText("Stop"))) {
      actionPerformed(it)
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

  companion object {
    private val ACTIVE_PROCESSES: Key<Int> = Key.create("ACTIVE_PROCESSES")
    private val SINGLE_RUNNING_NAME: Key<String> = Key.create("SINGLE_RUNNING_NAME")
  }
}

private class RunToolbarWidgetRunAction(
  executor: Executor,
  val hideIfDisable: Boolean = false,
  val settingSupplier: (Project) -> RunnerAndConfigurationSettings?,
) : ExecutorRegistryImpl.ExecutorAction(executor) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (hideIfDisable) {
      e.presentation.isVisible = e.presentation.isEnabled
    }
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? {
    return settingSupplier(e.project ?: return null)
  }

  companion object {
    @Nls
    fun reword(executor: Executor, restart: Boolean, configuration: String): String {
      return when {
        !restart -> ExecutionBundle.message("run.toolbar.widget.run.tooltip.text", executor.actionName, configuration)
        executor.id == RUN -> ExecutionBundle.message("run.toolbar.widget.rerun.text", configuration)
        else -> ExecutionBundle.message("run.toolbar.widget.restart.text", executor.actionName, configuration)
      }
    }
  }
}

private enum class RunButtonColors {
  BLUE {
    override fun updateColors(button: RunDropDownButton) {
      button.foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
      button.separatorColor = getColor("RunWidget.separatorColor") { ColorUtil.withAlpha(JBUI.CurrentTheme.RunWidget.FOREGROUND, 0.3) }
      button.background = getColor("RunWidget.background") { ColorUtil.fromHex("#3574F0") }
      button.hoverBackground = getColor("RunWidget.leftHoverBackground") { ColorUtil.fromHex("#3369D6") }
      button.pressedBackground = getColor("RunWidget.leftPressedBackground") { ColorUtil.fromHex("#315FBD") }
    }
  },
  GREEN {
    override fun updateColors(button: RunDropDownButton) {
      button.foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
      button.separatorColor = getColor("RunWidget.Running.separatorColor") { ColorUtil.withAlpha(JBUI.CurrentTheme.RunWidget.FOREGROUND, 0.3) }
      button.background = getColor("RunWidget.Running.background") { ColorUtil.fromHex("#599E5E") }
      button.hoverBackground = getColor("RunWidget.Running.leftHoverBackground") { ColorUtil.fromHex("#4F8453") }
      button.pressedBackground = getColor("RunWidget.Running.leftPressedBackground") { ColorUtil.fromHex("#456B47") }
    }
  },
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

  var dropDownPopup: ((DataContext) -> JBPopup)? by Delegates.observable(null) { prop, oldValue, newValue ->
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
      height = JBUIScale.scale(if (Registry.`is`("ide.experimental.ui.redesigned.run.widget")) RUN_TOOLBAR_HEIGHT else 26)
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

    constructor() {
      history = mutableSetOf()
    }

    internal constructor(history: MutableSet<Element>) {
      this.history = history
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
    _state = State(_state.history.take(30).toMutableList().apply {
      add(0, Element(setting.uniqueID))
    }.toMutableSet())
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

private class ExecutionReasonableHistoryManager : ProjectPostStartupActivity {
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
        } ?: thisLogger().warn(
          "Cannot find persisted configuration of '${env.runnerAndConfigurationSettings}'." +
          "It won't be saved in the run history."
        )
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

  fun isBusyState(): Boolean {
    return this == SCHEDULED || this == TERMINATING
  }

  fun isRunningState(): Boolean {
    return this == SCHEDULED || this == STARTED
  }
}

private fun isPersistedTask(env: ExecutionEnvironment): Boolean {
  return getPersistedConfiguration(env.runnerAndConfigurationSettings) != null
}

private fun getPersistedConfiguration(configuration: RunnerAndConfigurationSettings?): RunnerAndConfigurationSettings? {
  var conf: RunProfile = (configuration ?: return null).configuration
  if (conf is UserDataHolder) {
    conf = conf.getUserData(ExecutionManagerImpl.DELEGATED_RUN_PROFILE_KEY) ?: conf
  }
  return RunManager.getInstance(configuration.configuration.project).allSettings.find { it.configuration == conf }
}

private fun getConfigurations(manager: ExecutionManagerImpl, descriptor: RunContentDescriptor): RunnerAndConfigurationSettings? {
  return manager.getConfigurations(descriptor).firstOrNull()?.let(::getPersistedConfiguration)
}

@Nls
private fun RunnerAndConfigurationSettings.shortenName() = Executor.shortenNameIfNeeded(name)

@Nls
private fun shorten(@Nls text: String): String = StringUtil.shortenTextWithEllipsis(text, 27, 8)