// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.*
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.impl.*
import com.intellij.execution.impl.ExecutionManagerImpl.Companion.isProcessRunning
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.ui.customization.CustomizeActionGroupPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarProjectWidgetFactory
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory
import com.intellij.ui.*
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.popup.PopupState
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.annotations.*
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.*
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonListener
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicGraphicsUtils
import kotlin.concurrent.withLock
import kotlin.properties.Delegates

private const val RUN_TOOLBAR_WIDGET_GROUP = "RunToolbarWidgetCustomizableActionGroup"

internal class RunToolbarWidgetFactory : MainToolbarProjectWidgetFactory {
  override fun createWidget(project: Project): JComponent = RunToolbarWidget(project)
  override fun getPosition() = MainToolbarWidgetFactory.Position.Right
}

internal class RunToolbarWidgetCustomizableActionGroupProvider : CustomizableActionGroupProvider() {
  override fun registerGroups(registrar: CustomizableActionGroupRegistrar?) {
    if (ExperimentalUI.isNewToolbar()) {
      registrar?.addCustomizableActionGroup(RUN_TOOLBAR_WIDGET_GROUP, ExecutionBundle.message("run.toolbar.widget.customizable.group.name"))
    }
  }
}

internal class RunToolbarWidget(val project: Project) : JBPanel<RunToolbarWidget>(VerticalLayout(0)) {
  init {
    isOpaque = false
    add(createRunActionToolbar().component.apply {
      isOpaque = false
    }, VerticalLayout.CENTER)

    ExecutionReasonableHistoryManager.register(project)
  }

  private fun createRunActionToolbar(): ActionToolbar {
    val actionGroup = CustomActionsSchema.getInstance().getCorrectedAction(RUN_TOOLBAR_WIDGET_GROUP) as ActionGroup
    return ActionManager.getInstance().createActionToolbar(
      ActionPlaces.MAIN_TOOLBAR,
      actionGroup,
      true
    ).apply {
      targetComponent = null
      setReservePlaceAutoPopupIcon(false)
      layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    }
  }
}

internal class RunWithDropDownAction : AnAction(AllIcons.Actions.Execute), CustomComponentAction, DumbAware, UpdateInBackground {
  private val spinningIcon = SpinningProgressIcon()

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
    val project = e.project
    if (project == null) {
      e.presentation.icon = iconFor(LOADING)
      e.presentation.text = ExecutionBundle.message("run.toolbar.widget.loading.text")
      e.presentation.isEnabled = false
      return
    }
    val conf: RunnerAndConfigurationSettings? = RunManager.getInstance(project).selectedConfiguration
    val history = RunConfigurationStartHistory.getInstance(project)
    val run = history.firstOrNull(conf) { it.state.isRunningState() } ?: history.firstOrNull(conf)
    val isLoading = run?.state?.isBusyState() == true
    val lastExecutorId = run?.executorId ?: DefaultRunExecutor.EXECUTOR_ID
    e.presentation.putClientProperty(CONF, conf)
    e.presentation.putClientProperty(EXECUTOR_ID, lastExecutorId)
    if (conf != null) {
      val isRunning = run?.state == RunState.STARTED || run?.state == RunState.TERMINATING
      val canRestart = isRunning && !conf.configuration.isAllowRunningInParallel
      e.presentation.putClientProperty(COLOR, if (isRunning) RunButtonColors.GREEN else RunButtonColors.BLUE)
      e.presentation.icon = iconFor(when {
                                      isLoading -> LOADING
                                      canRestart -> RESTART
                                      else -> lastExecutorId
                                    })
      e.presentation.text = conf.shortenName()
      e.presentation.description = RunToolbarWidgetRunAction.reword(getExecutorByIdOrDefault(lastExecutorId), canRestart, conf.shortenName())
    } else {
      e.presentation.putClientProperty(COLOR, RunButtonColors.BLUE)
      e.presentation.icon = AllIcons.Actions.Execute
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

  private fun iconFor(executorId: String): Icon {
    return when (executorId) {
      DefaultRunExecutor.EXECUTOR_ID -> IconManager.getInstance().getIcon("expui/run/widget/run.svg", AllIcons::class.java)
      ToolWindowId.DEBUG -> IconManager.getInstance().getIcon("expui/run/widget/debug.svg", AllIcons::class.java)
      "Coverage" -> AllIcons.General.RunWithCoverage
      LOADING -> spinningIcon
      RESTART -> IconManager.getInstance().getIcon("expui/run/widget/restart.svg", AllIcons::class.java)
      else -> AllIcons.Actions.Execute
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return RunDropDownButton(presentation.text, presentation.icon).apply {
      addActionListener {
        val anActionEvent = AnActionEvent.createFromDataContext(place, presentation, DataManager.getInstance().getDataContext(this))
        if (it.modifiers and ActionEvent.SHIFT_MASK != 0) {
          ActionManager.getInstance().getAction("editRunConfigurations").actionPerformed(anActionEvent)
        }
        else if (it.modifiers  and ActionEvent.ALT_MASK != 0) {
          CustomizeActionGroupPanel.showDialog(RUN_TOOLBAR_WIDGET_GROUP, listOf(IdeActions.GROUP_NAVBAR_TOOLBAR), ExecutionBundle.message("run.toolbar.widget.customizable.group.dialog.title"))
            ?.let { result ->
              CustomizationUtil.updateActionGroup(result, RUN_TOOLBAR_WIDGET_GROUP)
              CustomActionsSchema.setCustomizationSchemaForCurrentProjects()
            }
        }
        else {
          actionPerformed(anActionEvent)
        }
      }
    }.let { Wrapper(it).apply { border = JBUI.Borders.emptyLeft(6) } }
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
      component.dropDownPopup = { context -> createPopup(context, context.getData(PlatformDataKeys.PROJECT)!!) }
    }
    component.toolTipText = presentation.description
    component.invalidate()
  }

  private fun createPopup(context: DataContext, project: Project): JBPopup {
    val actions = DefaultActionGroup()
    val registry = ExecutorRegistry.getInstance()
    val runExecutor = registry.getExecutorById(RUN) ?: error("No '$RUN' executor found")
    val debugExecutor = registry.getExecutorById(DEBUG) ?: error("No '$DEBUG' executor found")
    val profilerExecutor: ExecutorGroup<*>? = registry.getExecutorById(PROFILER) as? ExecutorGroup<*>
    actions.add(RunToolbarWidgetRunAction(runExecutor) { RunManager.getInstance(it).selectedConfiguration })
    actions.add(RunToolbarWidgetRunAction(debugExecutor) { RunManager.getInstance(it).selectedConfiguration })
    if (profilerExecutor != null) {
      actions.add(profilerExecutor.createExecutorActionGroup { RunManager.getInstance(it).selectedConfiguration })
    }
    actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.recent.separator.text")))
    RunConfigurationStartHistory.getInstance(project).history().mapTo(mutableSetOf()) { it.configuration }.forEach { conf ->
      actions.add(DefaultActionGroup(conf.shortenName(), true).apply {
        templatePresentation.icon = conf.configuration.icon
        if (conf.isRunning(project)) {
          templatePresentation.icon = ExecutionUtil.getLiveIndicator(templatePresentation.icon)
        }
        add(RunToolbarWidgetRunAction(runExecutor) { conf })
        add(RunToolbarWidgetRunAction(debugExecutor) { conf })
        if (profilerExecutor != null) {
          add(profilerExecutor.createExecutorActionGroup { conf })
        }
        add(Separator.create())
        add(DumbAwareAction.create(ExecutionBundle.message("run.toolbar.widget.dropdown.edit.text")) {
          it.project?.let { project ->
            EditConfigurationsDialog(project, object: ProjectRunConfigurationConfigurable(project) {
              override fun getSelectedConfiguration() = conf
            }).show()
          }
        })
        add(DumbAwareAction.create(ExecutionBundle.message("run.toolbar.widget.dropdown.delete.text")) {
          val prj = it.project ?: return@create
          if (Messages.showYesNoDialog(prj, ExecutionBundle.message("run.toolbar.widget.delete.dialog.message", conf.shortenName()), ExecutionBundle.message("run.toolbar.widget.delete.dialog.title"), null) == Messages.YES) {
            val runManager = RunManagerImpl.getInstanceImpl(prj)
            runManager.removeConfiguration(conf)
          }
        })
      })
    }
    actions.add(Separator.create())
    actions.add(DelegateAction({ ExecutionBundle.message("run.toolbar.widget.all.configurations") }, ActionManager.getInstance().getAction("ChooseRunConfiguration")))
    actions.add(ActionManager.getInstance().getAction("editRunConfigurations"))
    return JBPopupFactory.getInstance().createActionGroupPopup(
      null,
      actions,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true,
      ActionPlaces.getPopupPlace(ActionPlaces.MAIN_TOOLBAR)
    )
  }

  private fun ExecutorGroup<*>.createExecutorActionGroup(conf: (Project) -> RunnerAndConfigurationSettings?) = DefaultActionGroup().apply {
    templatePresentation.text = actionName
    isPopup = true

    childExecutors().forEach { executor ->
      add(RunToolbarWidgetRunAction(executor, hideIfDisable = true, conf))
    }
  }

  private class DelegateAction(val string: Supplier<@Nls String>, val delegate: AnAction) : AnAction() {
    override fun isDumbAware() = delegate.isDumbAware

    init {
      shortcutSet = delegate.shortcutSet
    }

    override fun update(e: AnActionEvent) {
      delegate.update(e)
      e.presentation.text = string.get()
    }

    override fun beforeActionPerformedUpdate(e: AnActionEvent) {
      delegate.beforeActionPerformedUpdate(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      delegate.actionPerformed(e)
    }
  }

  companion object {
    private val CONF: Key<RunnerAndConfigurationSettings> = Key.create("RUNNER_AND_CONFIGURATION_SETTINGS")
    private val COLOR: Key<RunButtonColors> = Key.create("RUN_BUTTON_COLOR")
    private val EXECUTOR_ID: Key<String> = Key.create("RUN_WIDGET_EXECUTOR_ID")

    const val RUN: String = DefaultRunExecutor.EXECUTOR_ID
    const val DEBUG: String = ToolWindowId.DEBUG
    const val PROFILER: String = "Profiler"
    const val LOADING: String = "Loading"
    const val RESTART: String = "Restart"
  }
}

class StopWithDropDownAction : AnAction(), CustomComponentAction, DumbAware, UpdateInBackground {

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
    e.presentation.icon = IconManager.getInstance().getIcon("expui/run/widget/stop.svg", AllIcons::class.java)
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
        actionPerformed(AnActionEvent.createFromDataContext(place, presentation, DataManager.getInstance().getDataContext(this)))
      }
      isPaintEnable = false
      isCombined = true
    }.let { Wrapper(it).apply { border = JBUI.Borders.emptyLeft(6) } }
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
    val project = e.project ?: return
    val configuration = getSelectedConfiguration(e) ?: return
    val isRunning = configuration.isRunning(project, myExecutor.id)
    if (isRunning && !configuration.configuration.isAllowRunningInParallel) {
      e.presentation.icon = AllIcons.Actions.Restart
      e.presentation.text = reword(myExecutor, true, configuration.shortenName())
    }
    else {
      e.presentation.icon = myExecutor.icon
      e.presentation.text = reword(myExecutor,false, configuration.shortenName())
    }
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
        executor.id == RunWithDropDownAction.RUN -> ExecutionBundle.message("run.toolbar.widget.rerun.text", configuration)
        else -> ExecutionBundle.message("run.toolbar.widget.restart.text", executor.actionName, configuration)
      }
    }
  }
}

@Suppress("UnregisteredNamedColor")
private enum class RunButtonColors {
  BLUE {
    override fun updateColors(button: RunDropDownButton) {
      button.foreground = getColor("RunWidget.foreground") { Color.WHITE }
      button.separatorColor = getColor("RunWidget.separatorColor") { ColorUtil.withAlpha(Color.WHITE, 0.3) }
      button.background = getColor("RunWidget.background") { ColorUtil.fromHex("#3574F0") }
      button.hoverBackground = getColor("RunWidget.leftHoverBackground") { ColorUtil.fromHex("#3369D6") }
      button.pressedBackground = getColor("RunWidget.leftPressedBackground") { ColorUtil.fromHex("#315FBD") }
    }
  },
  GREEN {
    override fun updateColors(button: RunDropDownButton) {
      button.foreground = getColor("RunWidget.Running.foreground") { Color.WHITE }
      button.separatorColor = getColor("RunWidget.Running.separatorColor") { ColorUtil.withAlpha(Color.WHITE, 0.3) }
      button.background = getColor("RunWidget.Running.background") { ColorUtil.fromHex("#599E5E") }
      button.hoverBackground = getColor("RunWidget.Running.leftHoverBackground") { ColorUtil.fromHex("#4F8453") }
      button.pressedBackground = getColor("RunWidget.Running.leftPressedBackground") { ColorUtil.fromHex("#456B47") }
    }
  },
  RED {
    override fun updateColors(button: RunDropDownButton) {
      button.foreground = getColor("RunWidget.StopButton.foreground") { Color.WHITE }
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
    b.foreground = Color.WHITE
    b.background = ColorUtil.fromHex("#3574F0")
    b.hoverBackground = ColorUtil.fromHex("#3369D6")
    b.pressedBackground = ColorUtil.fromHex("#315FBD")
    b.horizontalAlignment = SwingConstants.LEFT
  }

  override fun createButtonListener(b: AbstractButton?): BasicButtonListener {
    return MyHoverListener(b as RunDropDownButton)
  }

  override fun getPreferredSize(c: JComponent?): Dimension? {
    c as RunDropDownButton
    val prefSize = BasicGraphicsUtils.getPreferredButtonSize(c, c.iconTextGap)
    return prefSize?.apply {
      width = maxOf(width, if (c.isCombined) 0 else 72)
      height = 26
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

private fun RunnerAndConfigurationSettings.isRunning(project: Project, execId: String? = null) : Boolean {
  return with(ExecutionManagerImpl.getInstance(project)) {
    getRunningDescriptors {
      s -> s.isOfSameType(this@isRunning)
    }.any {
      if (execId != null) {
        getExecutors(it).asSequence().map(Executor::getId).contains(execId)
      } else {
        isProcessRunning(it)
      }
    }
  }
}

@State(name = "RunConfigurationStartHistory", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class RunConfigurationStartHistory(val project: Project) : PersistentStateComponent<RunConfigurationStartHistory.State> {

  class State {
    @XCollection(style = XCollection.Style.v2)
    @OptionTag("element")
    var history = mutableSetOf<Element>()
  }

  @Tag("element")
  data class Element(
    @Attribute
    var setting: String? = "",
    @Attribute
    var executorId: String = "",
  ) {
    @get:Transient
    var state: RunState = RunState.UNDEFINED
  }

  class RunElement(
    val configuration: RunnerAndConfigurationSettings,
    val executorId: String,
    state: RunState
  ) {
    var state: RunState = state
      set(value) {
        if (field != value) {
          getInstance(configuration.configuration.project)._state.history.find {
            it.setting == configuration.uniqueID && it.executorId == executorId
          }?.apply {
            state = value
          }
        }
        field = value
      }
  }

  fun history(): List<RunElement> {
    val settings = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }
    return _state.history.mapNotNull { settings[it.setting]?.let { setting ->
      RunElement(setting, it.executorId, it.state)
    } }
  }

  fun register(setting: RunnerAndConfigurationSettings, executorId: String, state: RunState) {
    _state.apply {
      history = history.take(30).toMutableList().apply {
        add(0, Element(setting.uniqueID, executorId).also {
          it.state = state
        })
      }.toMutableSet()
    }
  }

  fun firstOrNull(setting: RunnerAndConfigurationSettings?, predicate: Predicate<RunElement> = Predicate { true }): RunElement? {
    setting ?: return null
    return _state.history.firstOrNull {
      it.setting == setting.uniqueID && predicate.test(RunElement(setting, it.executorId, it.state))
    }?.let {
      RunElement(setting, it.executorId, it.state)
    }
  }

  private var _state = State()

  override fun getState() = _state

  override fun loadState(state: State) {
    _state = state
  }

  companion object {
    fun getInstance(project: Project): RunConfigurationStartHistory {
      return project.getService(RunConfigurationStartHistory::class.java)
    }
  }
}

/**
 * Registers one [ExecutionReasonableHistory] per project and
 * disposes it with the project.
 */
private object ExecutionReasonableHistoryManager {
  private val registeredListeners = mutableMapOf<Project, ExecutionReasonableHistory>()

  @RequiresEdt
  fun register(project: Project) {
    if (!registeredListeners.containsKey(project)) {
      registeredListeners[project] = ExecutionReasonableHistory(
        project,
        onHistoryChanged = ::processHistoryChanged,
        onAnyChange = ::configurationHistoryStateChanged
      )
      Disposer.register(project) {
        ApplicationManager.getApplication().invokeLater {
          unregister(project)
        }
      }
    }
  }

  /**
   * Unregister [ExecutionReasonableHistory] of the project and dispose it.
   *
   * Shouldn't be called directly because it clears runtime history,
   * that isn't persisted in [RunConfigurationStartHistory].
   */
  @RequiresEdt
  fun unregister(project: Project) {
    registeredListeners.remove(project)?.let(Disposer::dispose)
  }

  private fun processHistoryChanged(latest: ReasonableHistory.Elem<ExecutionEnvironment, RunState>?) {
    if (latest != null) {
      val (env, reason) = latest
      val history = RunConfigurationStartHistory.getInstance(env.project)
      getPersistedConfiguration(env.runnerAndConfigurationSettings)?.let { conf ->
        if (reason == RunState.SCHEDULED) {
          history.register(conf, env.executor.id, reason)
        }
        if (reason.isRunningState()) {
          RunManager.getInstance(env.project).selectedConfiguration = conf
        }
      } ?: logger<RunToolbarWidget>().error(java.lang.IllegalStateException("No setting for ${env.configurationSettings}"))
      ActivityTracker.getInstance().inc()
    }
  }

  private fun configurationHistoryStateChanged(env: ExecutionEnvironment, reason: RunState) {
    RunConfigurationStartHistory.getInstance(env.project).firstOrNull(
      getPersistedConfiguration(env.runnerAndConfigurationSettings)
    ) {
      env.executor.id == it.executorId
    }?.apply {
      state = reason
      ActivityTracker.getInstance().inc()
    }
  }
}

/**
 * Listens to process startup and finish.
 */
private class ExecutionReasonableHistory(
  project: Project,
  onHistoryChanged: (latest: Elem<ExecutionEnvironment, RunState>?) -> Unit,
  val onAnyChange: (ExecutionEnvironment, RunState) -> Unit
) : ReasonableHistory<ExecutionEnvironment, RunState>(onHistoryChanged), Disposable {

  init {
    Disposer.register(project, this)
    project.messageBus.connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
        if (isPersistedTask(env)) {
          advise(env, RunState.SCHEDULED)
        }
        onAnyChange(env, RunState.SCHEDULED)
      }

      override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        discard(env, RunState.NOT_STARTED)
        onAnyChange(env, RunState.NOT_STARTED)
      }

      override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (isPersistedTask(env)) {
          advise(env, RunState.STARTED)
        }
        onAnyChange(env, RunState.STARTED)
      }

      override fun processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (isPersistedTask(env)) {
          advise(env, RunState.TERMINATING)
        }
        onAnyChange(env, RunState.TERMINATING)
      }

      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        discard(env, RunState.TERMINATED)
        onAnyChange(env, RunState.TERMINATED)
      }
    })
  }

  override fun dispose() {
    history.clear()
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

/**
 * Tracks some values and returns the latest one.
 *
 * @param listener fired if current value is changed; same as [latest]
 */
private open class ReasonableHistory<T, R>(
  val listener: (latest: Elem<T, R>?) -> Unit
) {
  class Elem<T, R>(val value: T, var reason: R) {
    operator fun component1() = value
    operator fun component2() = reason
  }
  protected val history = mutableListOf<Elem<T, R>>()
  private val lock = ReentrantLock()

  /**
   * Returns the latest value in the history.
   */
  private val latest: Elem<T, R>?
    get() = lock.withLock { history.lastOrNull() }


  /**
   * Add a new value. If history doesn't contain the value or previous reason was different,
   * then adds and fires [listener]. Nothing will be changed if the value is already in the history or
   * the value is the latest but has same reason.
   */
  fun advise(value: T, reason: R) = lock.withLock {
    var l = latest
    if (l != null && l.value == value) {
      if (l.reason != reason) {
        l.reason = reason
        listener(l)
      }
      return
    }
    l = history.find { it.value == value }
    if (l != null) {
      // just update a reason
      l.reason = reason
      return
    }
    val newValue = Elem(value, reason)
    history += newValue
    listener(newValue)
  }

  /**
   * Removes value from the history. Also, if removed value was the latest, then fires [listener].
   */
  fun discard(value: T, reason: R) = lock.withLock {
    if (history.lastOrNull()?.value == value) {
      val oldValue = history.removeLast()
      oldValue.reason = reason
      listener(latest)
    }
    else {
      history.removeIf { it.value == value }
    }
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