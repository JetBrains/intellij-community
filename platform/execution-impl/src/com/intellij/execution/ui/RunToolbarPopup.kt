// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.*
import com.intellij.execution.actions.ExecutorAction
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.actions.RunConfigurationsComboBoxAction.SelectConfigAction
import com.intellij.execution.actions.RunCurrentFileExecutorAction
import com.intellij.execution.actions.RunSpecifiedConfigExecutorAction
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.dnd.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.actionSystem.remoting.ActionRemotePermissionRequirements
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.StackingPopupDispatcher
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.ColorUtil
import com.intellij.ui.GroupedElementsRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.PopupListElementRenderer
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
import kotlin.collections.plus
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max

private const val RUN: String = DefaultRunExecutor.EXECUTOR_ID
private const val DEBUG: String = ToolWindowId.DEBUG

private val recentLimit: Int get() = AdvancedSettings.getInt("max.recent.run.configurations")

@ApiStatus.Internal
@JvmField
val RUN_CONFIGURATION_KEY = DataKey.create<RunnerAndConfigurationSettings>("sub.popup.parent.action")

private const val TAG_PINNED = "pinned"
private const val TAG_RECENT = "recent"
private const val TAG_REGULAR_HIDE = "regular-hide" // hidden behind "All configurations" toggle
private const val TAG_REGULAR_SHOW = "regular-show" // shown regularly
private const val TAG_REGULAR_DUPE = "regular-dupe" // shown regularly until search (pinned/recent duplicate)
private const val TAG_HIDDEN = "hidden"             // hidden until search

class RunConfigurationsActionGroup : ActionGroup(), ActionRemoteBehaviorSpecification.BackendOnly {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return emptyArray()
    val selectedFile = e.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)?.file
    return createRunConfigurationsActionGroup(project, selectedFile).toTypedArray()
  }
}

internal fun createRunConfigurationsActionGroup(project: Project, selectedFile: VirtualFile?): List<AnAction> {
  val actions = ArrayList<AnAction>()
  val registry = ExecutorRegistry.getInstance()
  val runExecutor = registry.getExecutorById(RUN) ?: error("No '${RUN}' executor found")
  val debugExecutor = registry.getExecutorById(DEBUG) ?: error("No '${DEBUG}' executor found")

  val cfgMap = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }

  val pinnedIds = RunConfigurationStartHistory.getInstance(project).pinned()
  val historyIds = RunConfigurationStartHistory.getInstance(project).history()
  val pinned = pinnedIds.mapNotNull { cfgMap[it] }
  val recents = (historyIds - pinnedIds).asSequence().mapNotNull { cfgMap[it] }.take(max(recentLimit, 0)).toSet()

  val alreadyShownIds = HashSet<String>()
  if (pinned.isNotEmpty()) {
    actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.pinned.separator.text")))
    for (conf in pinned) {
      actions.add(createRunConfigurationWithInlines(project, conf, runExecutor, debugExecutor, true).also {
        it.templatePresentation.putClientProperty(ActionUtil.SEARCH_TAG, TAG_PINNED)
        it.templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, conf.folderName)
      })
      alreadyShownIds.add(conf.uniqueID)
    }
    actions.add(Separator.create())
  }

  val configurationMap = RunManagerImpl.getInstanceImpl(project).getConfigurationsGroupedByTypeAndFolder(true)
  val configurationCount = configurationMap.values.asSequence().map { it.values }.flatten().map { it.size }.sum()

  val withShowAllToggle = (recents.isNotEmpty() || pinned.isNotEmpty()) && configurationCount >= recentLimit
  if (withShowAllToggle) {
    actions.add(Separator.create(ExecutionBundle.message("run.toolbar.widget.dropdown.recent.separator.text")))
    for (conf in recents) {
      actions.add(createRunConfigurationWithInlines(project, conf, runExecutor, debugExecutor, false).also {
        it.templatePresentation.putClientProperty(ActionUtil.SEARCH_TAG, TAG_RECENT)
        it.templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, conf.folderName)
      })
      alreadyShownIds.add(conf.uniqueID)
    }
    actions.add(Separator.create())
    actions.add(ActionManager.getInstance().getAction("AllRunConfigurationsToggle"))
  }
  actions.add(createRunConfigurationActionGroup(configurationMap.values, alreadyShownIds, withShowAllToggle) {
    createRunConfigurationWithInlines(project, it, runExecutor, debugExecutor, false)
  })
  if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
    actions.add(createCurrentFileWithInlineActions(project, selectedFile, runExecutor, debugExecutor))
  }
  actions.add(Separator.create())
  actions.add(ActionManager.getInstance().getAction("editRunConfigurations"))
  return actions
}

private fun createRunConfigurationActionGroup(folderMaps: Collection<Map<String?, List<RunnerAndConfigurationSettings>>>,
                                              alreadyShownIds: Set<String>,
                                              withShowAllToggle: Boolean,
                                              actionCreator: (RunnerAndConfigurationSettings) -> AnAction): AnAction {
  val regularTag = if (withShowAllToggle) TAG_REGULAR_HIDE else TAG_REGULAR_SHOW
  return object : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
      val result = ArrayList<AnAction>()
      for (folderMap in folderMaps) {
        result.add(object : ActionGroup() {
          init {
            templatePresentation.putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true)
          }
          override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
            val result = ArrayList<AnAction>()
            for (folderEntry in folderMap.entries) {
              val folderName = folderEntry.key
              if (folderName == null) {
                folderEntry.value.forEach { cfg ->
                  val tag = if (alreadyShownIds.contains(cfg.uniqueID)) TAG_REGULAR_DUPE else regularTag
                  result.add(actionCreator(cfg).also {
                    it.templatePresentation.putClientProperty(ActionUtil.SEARCH_TAG, tag)
                  })
                }
              }
              else {
                result.add(object : ActionGroup() {
                  init {
                    templatePresentation.isPopupGroup =true
                    templatePresentation.setText(folderName)
                    templatePresentation.setIcon(AllIcons.Nodes.Folder)
                    templatePresentation.putClientProperty(ActionUtil.SEARCH_TAG, regularTag)
                    templatePresentation.putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true)
                  }

                  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
                    return folderEntry.value.map { cfg ->
                      actionCreator(cfg)
                    }.toTypedArray()
                  }
                })
              }
            }
            for (folderEntry in folderMap.entries) {
              if (folderEntry.key == null) continue
              folderEntry.value.forEach { cfg ->
                if (alreadyShownIds.contains(cfg.uniqueID)) return@forEach
                result.add(actionCreator(cfg).also {
                  it.templatePresentation.putClientProperty(ActionUtil.SEARCH_TAG, TAG_HIDDEN)
                  it.templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, folderEntry.key)
                })
              }
            }
            return result.toTypedArray()
          }
        })
        result.add(Separator.getInstance())
      }
      return result.toTypedArray()
    }
  }
}

@ApiStatus.Internal
@Topic.ProjectLevel
val VOID_EXECUTION_TOPIC = Topic("any execution event", Runnable::class.java)

@ApiStatus.Internal
private class RunPopupVoidExecutionListener(project: Project) : MyExecutionListener(
  { _, _, _ -> project.messageBus.syncPublisher(VOID_EXECUTION_TOPIC).run() })

internal class RunConfigurationsActionGroupPopup(actionGroup: ActionGroup,
                                                 dataContext: DataContext,
                                                 disposeCallback: (() -> Unit)?) :
  PopupFactoryImpl.ActionGroupPopup(
    null, null, actionGroup, dataContext,
    ActionPlaces.getPopupPlace("RunToolbarPopup"), PresentationFactory(),
    ActionPopupOptions.create(false, false, true, false, 30, false, null), disposeCallback) {

  private val pinnedSize: Int
  private val serviceState: RunConfigurationStartHistory

  init {
    serviceState = RunConfigurationStartHistory.getInstance(project)
    list.setExpandableItemsEnabled(false)
    (myStep as ActionPopupStep).setSubStepContextAdjuster { context, action ->
      if (action is SelectConfigAction) {
        CustomizedDataContext.withSnapshot(context) { sink ->
          sink[RUN_CONFIGURATION_KEY] = action.configuration
        }
      }
      else context
    }
    pinnedSize = (myStep as ActionPopupStep).values.asSequence()
      .filter { it.action !is Separator }
      .takeWhile { it.getClientProperty(ActionUtil.SEARCH_TAG) == TAG_PINNED }
      .count()
    if (pinnedSize != 0) {
      val dndManager = DnDManager.getInstance()
      dndManager.registerSource(MyDnDSource(), list, this)
      dndManager.registerTarget(MyDnDTarget(), list, this)
    }
    listModel.syncModel()

    project.messageBus.connect(this).subscribe(VOID_EXECUTION_TOPIC, Runnable {
      ApplicationManager.getApplication().invokeLater {
        if (list.isShowing) {
          (myStep as ActionPopupStep).updateStepItems(list)
          val focused = StackingPopupDispatcher.getInstance().focusedPopup
          if (focused != this@RunConfigurationsActionGroupPopup &&
              focused is ListPopupImpl) {
            (focused.step as ActionPopupStep).updateStepItems(focused.list)
          }
        }
      }
    })
  }

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    val popup = super.createPopup(parent, step, parentValue)
    popup.minimumSize = JBDimension(MINIMAL_POPUP_WIDTH, 0)
    return popup
  }

  override fun getListElementRenderer(): PopupListElementRenderer<PopupFactoryImpl.ActionItem> {
    return object : PopupListElementRenderer<PopupFactoryImpl.ActionItem>(this) {
      override fun isShowSecondaryText(): Boolean = mySpeedSearch.isHoldingFilter
    }
  }

  override fun shouldBeShowing(value: Any?): Boolean {
    @Suppress("SENSELESS_COMPARISON")
    if (serviceState == null) return true
    if (!super.shouldBeShowing(value)) return false
    if (value !is PopupFactoryImpl.ActionItem) return true
    val isFiltering = mySpeedSearch.isHoldingFilter
    val tag = value.getClientProperty(ActionUtil.SEARCH_TAG)
    return when {
      isFiltering -> tag != TAG_REGULAR_DUPE
      tag == TAG_REGULAR_SHOW -> true
      serviceState.state.allConfigurationsExpanded -> tag != TAG_HIDDEN
      else -> tag == null || tag == TAG_PINNED || tag == TAG_RECENT
    }
  }

  override fun getList(): JBList<*> {
    return super.getList() as JBList<*>
  }

  private data class DraggedIndex(val from: Int)

  private fun <E> offsetFromElementTopForDnD(list: JList<E>, dropTargetIndex: Int): Int {
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

open class AllRunConfigurationsToggle : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification {
  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Always
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.FrontendThenBackend

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
    val project = e.project ?: run {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
    e.presentation.isEnabledAndVisible = true
    e.presentation.icon =
      if (isSelected(e)) AllIcons.General.ChevronDown
      else AllIcons.General.ChevronRight

    if (e.presentation.text != templatePresentation.text) {
      // do not update the text when already showing
      return
    }

    val count = RunManager.getInstance(project).allSettings.size
    val textColor = ColorUtil.toHex(JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND)
    val message = ExecutionBundle.message("run.toolbar.widget.all.configurations", """<a style="color:#$textColor;">$count</a>""")
    e.presentation.text = "<html>$message</html>"
  }
}

private fun createRunConfigurationWithInlines(project: Project,
                                              conf: RunnerAndConfigurationSettings,
                                              runExecutor: Executor,
                                              debugExecutor: Executor,
                                              isPinned: Boolean): AnAction {
  val activeExecutor = getActiveExecutor(project, conf)
  val showRerunAndStopButtons = !conf.configuration.isAllowRunningInParallel && activeExecutor != null
  val resumeAction = InlineResumeCreator.getInstance(project).getInlineResumeCreator(conf, false)

  val inlineActions = listOf(
    RunSpecifiedConfigExecutorAction(runExecutor, conf, false),
    StopConfigurationInlineAction(runExecutor, conf),
    resumeAction,
    RunSpecifiedConfigExecutorAction(debugExecutor, conf, false),
    StopConfigurationInlineAction(debugExecutor, conf),
    (if (activeExecutor != null &&
         activeExecutor != runExecutor &&
         activeExecutor != debugExecutor)
      StopConfigurationInlineAction(activeExecutor, conf)
    else null))
    .filterNotNull()
  inlineActions.forEach {
    it.templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  val inlineActionGroup = DefaultActionGroup(inlineActions)
  val exclude = executorFilterByParentGroupFactory(DefaultActionGroup(inlineActions))
  val extraGroup = AdditionalRunningOptions.getInstance(project).getAdditionalActions(conf, false)
  return object : SelectConfigAction(project, conf) {

    override fun update(e: AnActionEvent) {
      super.update(e)
      val filtered = filterOutRunIfDebugResumeIsPresent(
        e, ActionGroupUtil.getVisibleActions(inlineActionGroup, e).toList())
      e.presentation.putClientProperty(ActionUtil.INLINE_ACTIONS, filtered)
      if (Registry.`is`("run.popup.show.inlines.for.active.configurations", false)) {
        val isRunning = getActiveExecutor(project, conf) != null
        filtered.forEach {
          e.updateSession.presentation(it).putClientProperty(ActionUtil.ALWAYS_VISIBLE_INLINE_ACTION, isRunning)
        }
      }
    }

    override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
      var prefix = listOf<AnAction>(extraGroup)
      if (showRerunAndStopButtons) {
        val extraExecutor = if (activeExecutor === runExecutor) debugExecutor else runExecutor
        prefix = prefix + RunSpecifiedConfigExecutorAction(extraExecutor, conf, false)
      }
      val pinAction = PinConfigurationAction(conf, isPinned)
      return (prefix + getDefaultChildren(exclude(e)) + pinAction).toTypedArray()
    }
  }
}

private fun createCurrentFileWithInlineActions(project: Project,
                                               selectedFile: VirtualFile?,
                                               runExecutor: Executor,
                                               debugExecutor: Executor): AnAction {
  if (DumbService.isDumb(project)) {
    return RunConfigurationsComboBoxAction.RunCurrentFileAction()
  }
  val psiFile = selectedFile?.findPsiFile(project)
  val configs = psiFile?.let { ExecutorAction.getRunConfigsForCurrentFile(it, false) } ?: emptyList()

  val runRunningConfig = configs.firstOrNull { checkIfRunWithExecutor(it, runExecutor, project) }
  val debugRunningConfig = configs.firstOrNull { checkIfRunWithExecutor(it, debugExecutor, project) }

  val inlineActions = listOf(
    RunCurrentFileExecutorAction(runExecutor),
    runRunningConfig?.let { StopConfigurationInlineAction(runExecutor, it) },
    RunCurrentFileExecutorAction(debugExecutor),
    debugRunningConfig?.let { StopConfigurationInlineAction(runExecutor, it) },
  ).filterNotNull()
  inlineActions.forEach {
    it.templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  val exclude = executorFilterByParentGroupFactory(DefaultActionGroup(inlineActions))
  return object : RunConfigurationsComboBoxAction.RunCurrentFileAction() {
    init {
      templatePresentation.putClientProperty(ActionUtil.INLINE_ACTIONS, inlineActions)
    }
    override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
      var prefix = emptyList<AnAction>()
      if (debugRunningConfig != null) prefix = prefix + RunCurrentFileExecutorAction(runExecutor)
      if (runRunningConfig != null) prefix = prefix + RunCurrentFileExecutorAction(debugExecutor)
      return (prefix + getDefaultChildren(exclude(e))).toTypedArray()
    }
  }
}

private fun checkIfRunWithExecutor(config: RunnerAndConfigurationSettings, executor: Executor, project: Project): Boolean {
  if (ProgramRunner.getRunner(executor.id, config.configuration) == null) return false
  return getActiveExecutor(project, config) === executor
}

private fun getActiveExecutor(project: Project, conf: RunnerAndConfigurationSettings): Executor? {
  val executionManager = ExecutionManagerImpl.getInstance(project)
  return executionManager.getRunningDescriptors { conf === it }.flatMap { executionManager.getExecutors(it) }.firstOrNull()
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

private class PinConfigurationAction(val conf: RunnerAndConfigurationSettings, isPinned: Boolean)
  : ActionRemotePermissionRequirements.ActionWithWriteAccess() {
  init {
    templatePresentation.text = getText(isPinned)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun getText(isPinned: Boolean): @Nls String =
    if (isPinned) ExecutionBundle.message("run.toolbar.widget.dropdown.unpin.action.text")
    else ExecutionBundle.message("run.toolbar.widget.dropdown.pin.action.text")

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.IfPreferred
    e.presentation.text = getText(RunConfigurationStartHistory.getInstance(project).pinned().contains(conf.uniqueID))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    RunConfigurationStartHistory.getInstance(project).togglePin(conf)
  }
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

  fun pinned(): Set<String> {
    return _state.pinned.asSequence().mapNotNull { it.setting }.toSet()
  }

  fun history(): Set<String> {
    return _state.history.asSequence().mapNotNull { it.setting }.toSet()
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
    _state = State(_state.history.take(max(5, _state.pinned.size + recentLimit * 2)).toMutableList().apply {
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
  override suspend fun execute(project: Project) {
    project.messageBus.simpleConnect().subscribe(ExecutionManager.EXECUTION_TOPIC, MyExecutionListener { executorId, env, state ->
      getPersistedConfiguration(env.runnerAndConfigurationSettings)?.let { conf ->
        RunStatusHistory.getInstance(env.project).changeState(conf, executorId, state)
      }
      ActivityTracker.getInstance().inc() // needed to update run toolbar
    })
  }
}

private open class MyExecutionListener(
  val onAnyChange: (executorId: String, env: ExecutionEnvironment, reason: RunState) -> Unit
) : ExecutionListener {
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
  val project = configuration.configuration.project ?: return null
  if (project.isDisposed) return null
  return RunManager.getInstance(project).allSettings.find { it.configuration == conf }
}

@Nls
private fun RunnerAndConfigurationSettings.shortenName() = Executor.shortenNameIfNeeded(name)
