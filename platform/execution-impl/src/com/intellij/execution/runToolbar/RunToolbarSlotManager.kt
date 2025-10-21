// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.execution.runToolbar

import com.intellij.CommonBundle
import com.intellij.execution.*
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.runToolbar.data.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.ActivityTracker
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RunToolbarSlotManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  companion object {
    private val LOG = logger<RunToolbarSlotManager>()

    fun getInstance(project: Project): RunToolbarSlotManager = project.service()

    @JvmField
    @Topic.ProjectLevel
    val RUN_TOOLBAR_SLOT_CONFIGURATION_MAP_TOPIC = Topic("RunToolbarWidgetSlotConfigurationMapChanged",
                                                         RWSlotsConfigurationListener::class.java)
  }

  private val runToolbarSettings = RunToolbarSettings.getInstance(project)

  internal val slotListeners = RWSlotController()
  internal val activeListener = RWAddedController()
  internal val stateListeners = RWStateController()

  internal var mainSlotData = SlotDate(project, UUID.randomUUID().toString())

  val activeProcesses = RWActiveProcesses()
  private val dataIds = mutableListOf<String>()

  private val slotsData = mutableMapOf<String, SlotDate>()

  private var activeDisposable: CheckedDisposable? = null

  private val processController = RWProcessController(project)

  internal var initialized: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      if(value) {
        activeListener.initialize()
      }
    }

  internal var active: Boolean = false
    set(value) {
      if (field == value) {
        return
      }

      field = value

      if (value) {
        if (RunToolbarProcess.logNeeded) LOG.info(
          "ACTIVE SM settings: new on top ${runToolbarSettings.getMoveNewOnTop()}; update by selected ${getUpdateMainBySelected()} RunToolbar")
        clear()

        val disp = Disposer.newCheckedDisposable()
        Disposer.register(project, disp)
        activeDisposable = disp

        val settingsData = runToolbarSettings.getConfigurations()
        val slotOrder = settingsData.first
        val configurations = settingsData.second

        slotOrder.filter { configurations[it] != null }.forEachIndexed { index, s ->
          if (index == 0) {
            traceState("SM reset main")

            slotsData.remove(mainSlotData.id)
            mainSlotData.updateId(s)
            mainSlotData.configuration = configurations[s]
            slotsData[mainSlotData.id] = mainSlotData

            traceState("SM after reset ")

          }
          else {
            addSlot(configurations[s], s)
          }
        }

        if (RunToolbarProcess.logNeeded) LOG.info("SM restoreRunConfigurations: ${configurations.values} RunToolbar")

        val con = project.messageBus.connect(disp)

        con.subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
          override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
            if (!getUpdateMainBySelected() || mainSlotData.configuration == settings) return

            mainSlotData.environment?.let {
              val slot = addSlot(settings)
              if (RunToolbarProcess.logNeeded) LOG.info("SM runConfigurationSelected: $settings first slot added RunToolbar")
              moveToTop(slot.id)
            } ?: kotlin.run {
              mainSlotData.configuration = settings
              if (RunToolbarProcess.logNeeded) LOG.info("SM runConfigurationSelected: $settings change main configuration RunToolbar")
              update()
            }
          }

          override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
            var changed = false
            slotsData.filter { it.value == settings && it.value.environment == null }.forEach {
              changed = true
              it.value.configuration = RunManager.getInstance(project).selectedConfiguration
            }
            if (changed) {
              update()
            }
          }
        })

        val executions = processController.getActiveExecutions()
        executions.filter { it.isRunning() == true }.forEach { addNewProcess(it) }
        activeListener.enabled()

        update()

        coroutineScope.launch(Dispatchers.EDT) {
          ActivityTracker.getInstance().inc()
        }
      }
      else {
        activeDisposable?.let {
          if (!it.isDisposed)
            Disposer.dispose(it)
          activeDisposable = null
        }

        activeListener.disabled()
        clear()
        if (RunToolbarProcess.logNeeded) LOG.info(
          "INACTIVE SM RunToolbar")

      }
      slotListeners.rebuildPopup()
      publishConfigurations(getConfigurationMap())
    }

  init {
    coroutineScope.launch(Dispatchers.EDT) {
      slotsData.put(mainSlotData.id, mainSlotData)

      activeListener.addListener(RunToolbarShortcutHelper(project))

      Disposer.register(project) {
        activeListener.clear()
        stateListeners.clear()
        slotListeners.clear()
      }
    }
  }

  private fun getUpdateMainBySelected(): Boolean {
    return runToolbarSettings.getUpdateMainBySelected()
  }

  private fun getMoveNewOnTop(executionEnvironment: ExecutionEnvironment): Boolean {
    if (!runToolbarSettings.getMoveNewOnTop()) {
      return false
    }
    val suppressValue = executionEnvironment.getUserData(RunToolbarProcessData.RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY) ?: false
    return !suppressValue
  }

  private fun clear() {
    dataIds.clear()
    mainSlotData.clear()

    slotsData.clear()
    slotsData[mainSlotData.id] = mainSlotData

    activeProcesses.clear()
    state = RWSlotManagerState.INACTIVE
  }

  private fun traceState(txt: String? = null) {
    if (!RunToolbarProcess.logNeeded) {
      return
    }

    txt?.let {  LOG.info(it) }
    LOG.info("SM state: $state mainSlot: ${mainSlotData} RunToolbar")
    LOG.info("SM slotsData: ${slotsData.values} RunToolbar")
  }

  private fun update() {
    saveSlotsConfiguration()
    updateState()

    if (!RunToolbarProcess.logNeeded) return
    LOG.trace("!!!!!UPDATE RunToolbar")
  }

  internal fun getMainOrFirstActiveProcess(): RunToolbarProcess? {
    return mainSlotData.environment?.getRunToolbarProcess() ?: activeProcesses.processes.keys.firstOrNull()
  }

  internal fun slotsCount(): Int {
    return dataIds.size
  }

  private var state: RWSlotManagerState = RWSlotManagerState.INACTIVE
    set(value) {
      if (value == field) return
      field = value
      traceState()
      stateListeners.stateChanged(value)
    }

  private fun updateState() {
    state = when (activeProcesses.getActiveCount()) {
      0 -> RWSlotManagerState.INACTIVE
      1 -> {
        mainSlotData.environment?.let {
          RWSlotManagerState.SINGLE_MAIN
        } ?: RWSlotManagerState.SINGLE_PLAIN
      }
      else -> {
        mainSlotData.environment?.let {
          RWSlotManagerState.MULTIPLE_WITH_MAIN
        } ?: RWSlotManagerState.MULTIPLE
      }
    }
  }

  internal fun getState(): RWSlotManagerState {
    return state
  }

  private fun getAppropriateSettings(env: ExecutionEnvironment): Iterable<SlotDate> {
    val sortedSlots = mutableListOf<SlotDate>()
    sortedSlots.add(mainSlotData)
    sortedSlots.addAll(dataIds.mapNotNull { slotsData[it] }.toList())
    return sortedSlots.filter { it.configuration == env.runnerAndConfigurationSettings }
  }

  internal fun processNotStarted(env: ExecutionEnvironment) {
  }

  internal fun processStarted(env: ExecutionEnvironment) {
    addNewProcess(env)
    update()
    coroutineScope.launch(Dispatchers.EDT) {
      ActivityTracker.getInstance().inc()
    }
  }

  private fun addNewProcess(env: ExecutionEnvironment) {
    val appropriateSettings = getAppropriateSettings(env)
    val emptySlotsWithConfiguration = appropriateSettings.filter { it.environment == null }

    var newSlot = false
    val slot = appropriateSettings.firstOrNull { it.environment?.executionId == env.executionId }
               ?: emptySlotsWithConfiguration.firstOrNull { slotData ->
                 env.runnerAndConfigurationSettings?.let {
                   slotData.id == env.dataContext?.getData(RunToolbarProcessData.RW_SLOT)
                 } ?: false
               }
               ?: emptySlotsWithConfiguration.firstOrNull()
               ?: kotlin.run {
                 newSlot = true
                 addSlot(env.runnerAndConfigurationSettings)
               }

    slot.environment = env
    activeProcesses.updateActiveProcesses(slotsData)

    if (!newSlot) {
      return
    }

    val runManager = RunManagerImpl.getInstanceImpl(project)

    val isCompoundProcess = env.getUserData(RunToolbarProcessData.RW_MAIN_CONFIGURATION_ID)?.let {
      runManager.getConfigurationById(it)?.configuration is CompoundRunConfiguration
    } ?: false

    if (isCompoundProcess || !getMoveNewOnTop(env)) {
      return
    }

    // RIDER-77316: we shouldn't move the main slot to the secondary slot list if this will create a duplicate configuration in the
    // secondary slots.

    // See the run widget guide for detailed algorithm and Cases explanation:
    // https://youtrack.jetbrains.com/articles/RIDER-A-1413/New-Toolbar-Run-Widget-Guide
    val secondarySlotsContainingMainSlotConfig = dataIds
      .asSequence()
      .mapNotNull { slotsData[it] }
      .filter { it.configuration == mainSlotData.configuration }
      .toList()

    if (secondarySlotsContainingMainSlotConfig.isEmpty()) {
      // Case 1: there are no slots corresponding to the main one in the secondary slot list. So, move the new slot to the main
      // position, and move the former main slot to the secondary slot list (effectively creating a new secondary slot).
      moveToTop(slot.id)
    }
    else {
      // Case 2: there's at least one secondary slot corresponding to the main one.

      fun removeFormerMainSlotAndMoveCurrentToTop() {
        val formerMain = mainSlotData
        formerMain.environment = null // deactivate to avoid any UI questions when removing it
        moveToTop(slot.id)
        removeSlot(formerMain.id) // delete completely
      }

      if (mainSlotData.environment != null) {
        // Case 2.1: the former main slot has an active process, so we'll need to mark one of the corresponding secondary slots as
        // active instead:
        val freeSlotCorrespondingToMain = secondarySlotsContainingMainSlotConfig.firstOrNull { it.environment == null }
        if (freeSlotCorrespondingToMain != null) {
          // Case 2.1.1: we've found the new free slot to enable the (former) main configuration there.
          freeSlotCorrespondingToMain.environment = mainSlotData.environment
          removeFormerMainSlotAndMoveCurrentToTop()
        }
        else {
          // Case 2.1.2: there are no free slots for the former main active configuration to move to. Add a new one then.
          moveToTop(slot.id)
        }
      }
      else {
        // Case 2.2: the former main slot is inactive. Let's just remove it after replacement.
        removeFormerMainSlotAndMoveCurrentToTop()
      }
    }
  }

  fun processTerminating(env: ExecutionEnvironment) {
    slotsData.values.firstOrNull { it.environment?.executionId == env.executionId }?.let {
      it.environment = env
    }
    activeProcesses.updateActiveProcesses(slotsData)
    updateState()
    coroutineScope.launch(Dispatchers.EDT) {
      ActivityTracker.getInstance().inc()
    }
  }

  fun processTerminated(executionId: Long) {
    slotsData.values.firstOrNull { it.environment?.executionId == executionId }?.let { slotDate ->
      val removable = slotDate.environment?.runnerAndConfigurationSettings?.let {
        !RunManager.getInstance(project).hasSettings(it)
      } ?: true

      if (removable) {
        if (slotDate == mainSlotData && slotsData.size == 1) {
          slotDate.clear()
          slotDate.configuration = RunManager.getInstance(project).selectedConfiguration
        }
        else {
          removeSlot(slotDate.id)
        }
      }
      else {
        slotDate.environment = null
      }
    }

    if (RunToolbarProcess.logNeeded) LOG.info("SM process stopped: $executionId RunToolbar")
    activeProcesses.updateActiveProcesses(slotsData)
    updateState()

    coroutineScope.launch(Dispatchers.EDT) {
      ActivityTracker.getInstance().inc()
    }
  }

  internal fun addAndSaveSlot(): SlotDate {
    val slot = addSlot()
    saveSlotsConfiguration()
    return slot
  }

  private fun addSlot(configuration: RunnerAndConfigurationSettings? = null, id: String = UUID.randomUUID().toString()): SlotDate {
    val slot = SlotDate(project, id)
    slot.configuration = configuration
    dataIds.add(slot.id)
    slotsData[slot.id] = slot

    slotListeners.slotAdded()

    return slot
  }

  internal fun getData(index: Int): SlotDate? {
    return if (index >= 0 && index < dataIds.size) {
      dataIds[index].let {
        slotsData[it]
      }
    }
    else null
  }


  /**
   * Make the slot with [id] the main slot, moving the former main slot to the secondary slot list. This function is no-op if the slot with
   * the passed [id] is already the main one.
   */
  internal fun moveToTop(id: String) {
    if (mainSlotData.id == id) return

    slotsData[id]?.let { newMain ->
      val oldMain = mainSlotData
      mainSlotData = newMain

      dataIds.remove(id)
      dataIds.add(0, oldMain.id)
    }

    update()
  }

  internal fun removeSlot(id: String) {
    val index = dataIds.indexOf(id)

    fun remove() {
      if (id == mainSlotData.id) {
        if (dataIds.isNotEmpty()) {
          val firstSlotId = dataIds[0]
          slotsData[firstSlotId]?.let {
            mainSlotData = it
            slotsData.remove(id)
            dataIds.remove(it.id)
          }
        }
      }
      else {
        slotsData.remove(id)
        dataIds.remove(id)
      }

      coroutineScope.launch(Dispatchers.EDT) {
        slotListeners.slotRemoved(index)
        ActivityTracker.getInstance().inc()
      }
    }

    (if (index >= 0) getData(index) else if (mainSlotData.id == id) mainSlotData else null)?.let { slotDate ->
      slotDate.environment?.let {
        if (it.isRunning() != true) {
          remove()
        }
        else if (Messages.showOkCancelDialog(
            project,
            LangBundle.message("run.toolbar.remove.active.process.slot.message"),
            LangBundle.message("run.toolbar.remove.active.process.slot.title", it.runnerAndConfigurationSettings?.name ?: ""),
            LangBundle.message("run.toolbar.remove.active.process.slot.ok"),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon()/*, object : DoNotAskOption.Adapter() {
              override fun rememberChoice(isSelected: Boolean, exitCode: Int) {

              }
            }*/) == Messages.OK) {
          it.contentToReuse?.let {
            ExecutionManagerImpl.stopProcess(it)
          }

          remove()
        }
      } ?: run {
        remove()
      }
    } ?: slotListeners.rebuildPopup()

    update()
  }

  internal fun configurationChanged(slotId: String, configuration: RunnerAndConfigurationSettings?) {
    coroutineScope.launch(Dispatchers.EDT) {
      project.messageBus.syncPublisher(RUN_TOOLBAR_SLOT_CONFIGURATION_MAP_TOPIC).configurationChanged(slotId, configuration)
    }
    saveSlotsConfiguration()
  }

  private fun saveSlotsConfiguration() {
    val runManager = project.serviceIfCreated<RunManager>()
    if (runManager != null) {
      mainSlotData.configuration?.let {
        if (runManager.hasSettings(it) &&
            it != runManager.selectedConfiguration &&
            mainSlotData.environment?.getRunToolbarProcess()?.isTemporaryProcess() != true) {
          runManager.selectedConfiguration = mainSlotData.configuration
          if (RunToolbarProcess.logNeeded) LOG.info(
            "MANAGER saveSlotsConfiguration. change selected configuration by main: ${mainSlotData.configuration} RunToolbar")
        }
      }
    }

    val slotOrder = getSlotOrder()
    val configurations = getConfigurationMap(slotOrder)
    if (RunToolbarProcess.logNeeded) {
      LOG.info("MANAGER saveSlotsConfiguration: ${configurations} RunToolbar")
    }

    runToolbarSettings.setConfigurations(configurations, slotOrder)
    publishConfigurations(configurations)
  }

  private fun getSlotOrder(): List<String> {
    val list = mutableListOf<String>()
    list.add(mainSlotData.id)
    list.addAll(dataIds)
    return list
  }

  private fun getConfigurationMap(slotOrder: List<String>): Map<String, RunnerAndConfigurationSettings?> {
    return slotOrder.associateWith { slotsData[it]?.configuration }
  }

  private fun getConfigurationMap(): Map<String, RunnerAndConfigurationSettings?> {
    return getConfigurationMap(getSlotOrder())
  }

  private fun publishConfigurations(slotConfigurations: Map<String, RunnerAndConfigurationSettings?>) {
    coroutineScope.launch(Dispatchers.EDT) {
      project.messageBus.syncPublisher(RUN_TOOLBAR_SLOT_CONFIGURATION_MAP_TOPIC).slotsConfigurationChanged(slotConfigurations)
    }
  }
}

internal open class SlotDate(val project: Project, override var id: String) : RunToolbarData {
  companion object {
    var index = 0
  }

  fun updateId(value: String) {
    id = value
  }

  override var executionTarget: ExecutionTarget? = null
    get() = environment?.executionTarget ?: run {
      configuration?.configuration.let {
        val targets = ExecutionTargetManager.getInstance(project).getTargetsFor(it)
        if (field != null && targets.contains(field)) field else targets.firstOrNull()
      }
    } ?: run {
      ExecutionTargetManager.getInstance(project).activeTarget
    }

  override var configuration: RunnerAndConfigurationSettings? = null
    get() = environment?.runnerAndConfigurationSettings ?: field


  override var environment: ExecutionEnvironment? = null
    set(value) {
      if (field != value)
        field = value
      value?.let {
        configuration = it.runnerAndConfigurationSettings
        executionTarget = it.executionTarget
      }
    }

  override fun clear() {
    environment = null
  }

  override fun toString(): String {
    return "$id - ${environment?.let { "$it ${it.executor.actionName} ${it.executionId}" } ?: configuration?.configuration?.name ?: "configuration null"}"
  }
}
