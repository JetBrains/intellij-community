// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ide.dnd.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.HideableAction
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.remoting.ActionRemotePermissionRequirements
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.GroupedElementsRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.KeepingPopupOpenAction
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Point
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Predicate
import javax.swing.JList
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max

private const val RUN: String = DefaultRunExecutor.EXECUTOR_ID
private const val DEBUG: String = ToolWindowId.DEBUG

private val recentLimit: Int get() = AdvancedSettings.getInt("max.recent.run.configurations")

@ApiStatus.Internal
val RUN_CONFIGURATION_KEY = DataKey.create<RunnerAndConfigurationSettings>("sub.popup.parent.action")

internal fun createRunConfigurationsActionGroup(project: Project, e: AnActionEvent): ActionGroup {
  val actions = DefaultActionGroup()
  val registry = ExecutorRegistry.getInstance()
  val runExecutor = registry.getExecutorById(RUN) ?: error("No '${RUN}' executor found")
  val debugExecutor = registry.getExecutorById(DEBUG) ?: error("No '${DEBUG}' executor found")
  val pinned = RunConfigurationStartHistory.getInstance(project).pinned()
  val recents = RunConfigurationStartHistory.getInstance(project).historyWithoutPinned().take(max(recentLimit, 0))
  var shouldShowRecent: Boolean = recents.isNotEmpty() || pinned.isNotEmpty()

  val shouldBeShown = { configuration: RunnerAndConfigurationSettings?, holdingFilter: Boolean ->
    when {
      !shouldShowRecent -> true
      holdingFilter && configuration != null -> !recents.contains(configuration)
      holdingFilter -> true
      else -> RunConfigurationStartHistory.getInstance(project).state.allConfigurationsExpanded
    }
  }

  val createActionFn: (RunnerAndConfigurationSettings) -> AnAction = { configuration ->
    createRunConfigurationWithInlines(runExecutor, debugExecutor, configuration, project, e, pinned) { shouldBeShown(configuration, it) }
  }
  val createFolderFn: (String) -> DefaultActionGroup = { folderName ->
    HideableDefaultActionGroup(folderName) { shouldBeShown(null, it) }
  }
  val filteringSubActions: (RunnerAndConfigurationSettings, String) -> AnAction = { configuration, folderName ->
    createRunConfigurationWithInlines(runExecutor, debugExecutor, configuration, project, e, pinned) { holdingFilter ->
      holdingFilter && !recents.contains(configuration)
    }.also {
      it.templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, folderName)
    }
  }
  val allConfigurations = DefaultActionGroup()
  val allConfigurationsNumber = RunConfigurationsComboBoxAction.addRunConfigurations(allConfigurations, project, createActionFn, createFolderFn, filteringSubActions)

  if (shouldShowRecent && allConfigurationsNumber < recentLimit) {
    shouldShowRecent = false
  }

  if (pinned.isNotEmpty()) {
    actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.pinned.separator.text")))
    for (conf in pinned) {
      val actionGroupWithInlineActions = createRunConfigurationWithInlines(runExecutor, debugExecutor, conf, project, e, pinned)
      actions.add(actionGroupWithInlineActions)
    }
    actions.add(Separator.create())
  }

  if (shouldShowRecent) {
    actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.recent.separator.text")))
    for (conf in recents) {
      val actionGroupWithInlineActions = createRunConfigurationWithInlines(runExecutor, debugExecutor, conf, project, e, pinned)
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

  private val pinnedSize = RunConfigurationStartHistory.getInstance(project).pinned().size

  init {
    list.setExpandableItemsEnabled(false)
    (step as ActionPopupStep).setSubStepContextAdjuster { context, action ->
      if (action is SelectConfigAction) {
        CustomizedDataContext.create(context) { dataId ->
          if (RUN_CONFIGURATION_KEY.`is`(dataId)) action.configuration else null
        }
      }
      else context
    }
    if (pinnedSize != 0) {
      val dndManager = DnDManager.getInstance()
      dndManager.registerSource(MyDnDSource(), list, this)
      dndManager.registerTarget(MyDnDTarget(), list, this)
    }
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

  override fun getList(): JBList<*> {
    return super.getList() as JBList<*>
  }

  private data class DraggedIndex(val from: Int)

  private fun<E> offsetFromElementTopForDnD(list: JList<E>, dropTargetIndex: Int): Int {
    if (dropTargetIndex == pinnedSize) {
      return 0
    }
    val elementAt = list.model.getElementAt(dropTargetIndex)
    val c: Component = list.cellRenderer.getListCellRendererComponent(list, elementAt, dropTargetIndex, false, false)
    return if (c is GroupedElementsRenderer.MyComponent && StringUtil.isNotEmpty(c.separator.caption)) {
      c.separator.preferredSize.height
    }
    else 0
  }

  private inner class MyDnDTarget : DnDTarget {
    override fun update(aEvent: DnDEvent): Boolean {
      val from = (aEvent.attachedObject as? DraggedIndex)?.from
      if (from is Int) {
        val targetIndex: Int = list.locationToIndex(aEvent.point)
        val possible: Boolean = (0 until pinnedSize + 1).contains(targetIndex)
        list.setDropTargetIndex(if (possible && wouldActuallyMove(from, targetIndex)) targetIndex else -1)
        aEvent.isDropPossible = possible
      }
      else {
        aEvent.isDropPossible = false
      }
      return false
    }

    override fun drop(aEvent: DnDEvent) {
      list.setDropTargetIndex(-1)
      val from = (aEvent.attachedObject as? DraggedIndex)?.from
      if (from is Int) {
        val targetIndex: Int = list.locationToIndex(aEvent.point)
        if (wouldActuallyMove(from, targetIndex)) {
          (step as ActionPopupStep).reorderItems(from, targetIndex, 0)
          RunConfigurationStartHistory.getInstance(project).reorderItems(from, targetIndex)
          listModel.syncModel()
        }
      }
    }

    private fun wouldActuallyMove(from: Int, targetIndex: Int) = from != targetIndex && from + 1 != targetIndex

    override fun cleanUpOnLeave() {
      list.setDropTargetIndex(-1)
    }
  }

  private inner class MyDnDSource : DnDSource {
    override fun canStartDragging(action: DnDAction, dragOrigin: Point): Boolean {
      return (0 until pinnedSize).contains(list.locationToIndex(dragOrigin))
    }

    override fun startDragging(action: DnDAction, dragOrigin: Point): DnDDragStartBean? {
      val index: Int = list.locationToIndex(dragOrigin)
      if (index < 0) return null
      list.setOffsetFromElementTopForDnD { offsetFromElementTopForDnD(list, it) }
      return DnDDragStartBean(DraggedIndex(index))
    }
  }
}

private class HideableDefaultActionGroup(@NlsSafe name: String, override val shouldBeShown: (holdingFilter: Boolean) -> Boolean)
  : DefaultActionGroup({ name }, true), DumbAware, HideableAction

class AllRunConfigurationsToggle(@NlsActions.ActionText text: String) : ToggleAction(text), KeepingPopupOpenAction, DumbAware {

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
                                              pinned: List<RunnerAndConfigurationSettings>,
                                              shouldBeShown: (Boolean) -> Boolean = { true }
): SelectRunConfigurationWithInlineActions {

/*  val e = event.withDataContext(CustomizedDataContext.create(event.dataContext) { dataId ->
    if (RUN_CONFIGURATION_KEY.`is`(dataId)) conf else null
  })*/

  val activeExecutor = getActiveExecutor(project, conf)
  val showRerunAndStopButtons = !conf.configuration.isAllowRunningInParallel && activeExecutor != null
  val inlineActions = ArrayList<AnAction>()
  if (showRerunAndStopButtons) {
    if(RunWidgetResumeManager.getInstance(project).isSecondVersionAvailable()) {
      InlineResumeCreator.getInstance(project).getInlineResumeCreator(conf, false)?.let {
        inlineActions.add(it)
      }
    }

    inlineActions.addAll(listOf(
      ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(activeExecutor!!, conf, false),
      StopConfigurationInlineAction(activeExecutor, conf)
    ))
  } else {
    inlineActions.addAll(listOf(
      ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(runExecutor, conf, false),
      ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(debugExecutor, conf, false)
    ))
  }

  val result = SelectRunConfigurationWithInlineActions(inlineActions, conf, project, shouldBeShown)
  if (showRerunAndStopButtons) {
    val extraExecutor = if (activeExecutor === runExecutor) debugExecutor else runExecutor
    val extraAction = ExecutorRegistryImpl.RunSpecifiedConfigExecutorAction(extraExecutor, conf, false)
    result.addAction(extraAction, Constraints.FIRST)
  }
  val wasPinned = pinned.contains(conf)
  val text = if (wasPinned) ExecutionBundle.message("run.toolbar.widget.dropdown.unpin.action.text")
  else ExecutionBundle.message("run.toolbar.widget.dropdown.pin.action.text")
  result.addAction(object : ActionRemotePermissionRequirements.ActionWithWriteAccess(text) {
    override fun actionPerformed(e: AnActionEvent) {
      RunConfigurationStartHistory.getInstance(project).togglePin(conf)
    }
  })
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

private class StopConfigurationInlineAction(val executor: Executor, val settings: RunnerAndConfigurationSettings)
  : AnAction(), ActionRemotePermissionRequirements.RunAccess {

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

    @XCollection(style = XCollection.Style.v2)
    @OptionTag("element")
    var pinned: MutableSet<Element>

    var allConfigurationsExpanded: Boolean

    constructor() {
      history = mutableSetOf()
      pinned = mutableSetOf()
      allConfigurationsExpanded = false
    }

    internal constructor(history: MutableSet<Element>, pinned: MutableSet<Element>, allConfigurationsExpanded: Boolean) {
      this.history = history
      this.pinned = LinkedHashSet(pinned)
      this.allConfigurationsExpanded = allConfigurationsExpanded
    }
  }

  @Tag("element")
  data class Element(
    @Attribute
    var setting: String? = "",
  )

  fun pinned(): List<RunnerAndConfigurationSettings> {
    val settings = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }
    return _state.pinned.mapNotNull { settings[it.setting] }
  }

  fun historyWithoutPinned(): List<RunnerAndConfigurationSettings> {
    val settings = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }
    return (_state.history - _state.pinned).mapNotNull { settings[it.setting] }
  }

  fun history(): List<RunnerAndConfigurationSettings> {
    val settings = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }
    return _state.history.mapNotNull { settings[it.setting] }
  }

  fun togglePin(setting: RunnerAndConfigurationSettings) {
    val element = Element(setting.uniqueID)
    val wasPinned = _state.pinned.contains(element)
    val newPinned = _state.pinned.toMutableList().also {
      if (wasPinned) {
        it.remove(element)
      }
      else {
        it.add(element)
      }
    }.toMutableSet()
    _state = State(_state.history, newPinned, _state.allConfigurationsExpanded)
    project.messageBus.syncPublisher(TOPIC).togglePin(setting)
  }

  fun reorderItems(from: Int, where: Int) {
    val list = _state.pinned.toMutableList()
    list.add(where, list[from])
    list.removeAt(if (where < from) from + 1 else from)
    _state = State(_state.history, list.toMutableSet(), _state.allConfigurationsExpanded)
  }

  fun register(setting: RunnerAndConfigurationSettings) {
    _state = State(_state.history.take(max(5, _state.pinned.size + recentLimit*2)).toMutableList().apply {
      add(0, Element(setting.uniqueID))
    }.toMutableSet(), _state.pinned, _state.allConfigurationsExpanded)
    project.messageBus.syncPublisher(TOPIC).register(setting)
  }

  private var _state = State()

  override fun getState() = _state

  override fun loadState(state: State) {
    _state = state
  }

  fun reloadState() {
    _state = State(_state.history, _state.pinned, _state.allConfigurationsExpanded)
  }

  interface Listener {
    fun togglePin(setting: RunnerAndConfigurationSettings) {}
    fun register(setting: RunnerAndConfigurationSettings) {}
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunConfigurationStartHistory = project.service()

    @Topic.ProjectLevel
    val TOPIC = Topic("RunConfigurationStartHistory events", Listener::class.java)
  }
}

private class ExecutionReasonableHistoryManager : ProjectActivity {
  override suspend fun execute(project: Project) : Unit = blockingContext {
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
        }
        ActivityTracker.getInstance().inc() // needed to update run toolbar
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

@Nls
private fun RunnerAndConfigurationSettings.shortenName() = Executor.shortenNameIfNeeded(name)
