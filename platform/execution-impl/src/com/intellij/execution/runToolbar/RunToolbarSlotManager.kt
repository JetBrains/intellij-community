// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.CommonBundle
import com.intellij.execution.IS_RUN_MANAGER_INITIALIZED
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runToolbar.data.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.ActivityTracker
import com.intellij.lang.LangBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AppUIUtil
import com.intellij.util.messages.Topic
import java.util.*
import javax.swing.SwingUtilities

class RunToolbarSlotManager(val project: Project) {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarSlotManager::class.java)
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

  internal var mainSlotData = SlotDate(UUID.randomUUID().toString())

  val activeProcesses = RWActiveProcesses()
  private val dataIds = mutableListOf<String>()

  private val slotsData = mutableMapOf<String, SlotDate>()

  private var activeDisposable: CheckedDisposable? = null

  private val processController = RWProcessController(project)

  internal var active: Boolean = false
    set(value) {
      if (field == value) return

      field = value

      if (value) {
        if (RunToolbarProcess.logNeeded) LOG.info(
          "ACTIVE SM settings: new on top ${runToolbarSettings.getMoveNewOnTop()}; update by selected ${getUpdateMainBySelected()} RunToolbar")
        clear()

        val disp = Disposer.newCheckedDisposable()
        Disposer.register(project, disp)
        activeDisposable = disp

        val settingsData =  runToolbarSettings.getConfigurations()
        val slotOrder = settingsData.first
        val configurations = settingsData.second

        slotOrder.filter { configurations[it] != null }.forEachIndexed { index, s ->
          if (index == 0) {
            mainSlotData.updateId(s)
            mainSlotData.configuration = configurations[s]
            slotsData[mainSlotData.id] = mainSlotData
          }
          else {
            addSlot(s).configuration = configurations[s]
          }
        }

        if (RunToolbarProcess.logNeeded) LOG.info("SM restoreRunConfigurations: ${configurations.values} RunToolbar")

        val con = project.messageBus.connect(disp)

        con.subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
          override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
            if (!getUpdateMainBySelected() || mainSlotData.configuration == settings) return

            mainSlotData.environment?.let {
              val slot = addSlot()
              slot.configuration = settings
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

        SwingUtilities.invokeLater {
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

  private fun getUpdateMainBySelected(): Boolean {
    return runToolbarSettings.getUpdateMainBySelected()
  }

  private fun getMoveNewOnTop(executionEnvironment: ExecutionEnvironment): Boolean {
    if (!runToolbarSettings.getMoveNewOnTop()) return false
    val suppressValue = executionEnvironment.getUserData(RunToolbarData.RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY) ?: false
    return !suppressValue
  }

  private fun clear() {
    mainSlotData.clear()
    dataIds.clear()
    slotsData.clear()

    activeProcesses.clear()
    state = RWSlotManagerState.INACTIVE
  }


  private fun traceState() {
    if (!RunToolbarProcess.logNeeded) return

    val separator = " "
    val ids = dataIds.indices.mapNotNull { "${it + 1}: ${slotsData[dataIds[it]]}" }.joinToString(", ")
    LOG.info("SM state: $state" +
             "${separator}== slots: 0: ${mainSlotData}, $ids" +
             "${separator}== slotsData: ${slotsData.values} RunToolbar")
  }


  init {
    SwingUtilities.invokeLater {
      if (project.isDisposed) return@invokeLater

      slotsData[mainSlotData.id] = mainSlotData

      activeListener.addListener(RunToolbarShortcutHelper(project))

      Disposer.register(project) {
        activeListener.clear()
        stateListeners.clear()
        slotListeners.clear()
      }
    }
  }

  private fun update() {
    saveSlotsConfiguration()
    updateState()

    LOG.trace("!!!!!UPDATE RunToolbar")
  }

  internal fun startWaitingForAProcess(slotDate: RunToolbarData, settings: RunnerAndConfigurationSettings, executorId: String) {
    slotsData.values.forEach {
      val waitingForAProcesses = it.waitingForAProcesses
      if (slotDate == it) {
        waitingForAProcesses.start(project, settings, executorId)
      }
      else {
        if (waitingForAProcesses.isWaitingForASubProcess(settings, executorId)) {
          waitingForAProcesses.clear()
        }
      }
    }
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
    val config = env.runnerAndConfigurationSettings ?: return

    val appropriateSettings = getAppropriateSettings(env)
    val emptySlotsWithConfiguration = appropriateSettings.filter { it.environment == null }

    emptySlotsWithConfiguration.map { it.waitingForAProcesses }.firstOrNull {
      it.isWaitingForASingleProcess(config, env.executor.id)
    }?.clear() ?: run {
      slotsData.values.filter { it.configuration?.configuration is CompoundRunConfiguration }.firstOrNull { slotsData ->
        slotsData.waitingForAProcesses.isWaitingForASubProcess(config, env.executor.id)
      }?.clear()
    }
  }

  internal fun processStarted(env: ExecutionEnvironment) {
    addNewProcess(env)
    update()
    SwingUtilities.invokeLater {
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
                   slotData.waitingForAProcesses.isWaitingForASingleProcess(it, env.executor.id)
                 } ?: false
               }
               ?: emptySlotsWithConfiguration.firstOrNull()
               ?: kotlin.run {
                 newSlot = true
                 addSlot()
               }

    slot.environment = env
    activeProcesses.updateActiveProcesses(slotsData)

    if (newSlot) {
      val isCompoundProcess = slotsData.values.filter { it.configuration?.configuration is CompoundRunConfiguration }.firstOrNull { slotsData ->
        env.runnerAndConfigurationSettings?.let {
          slotsData.waitingForAProcesses.checkAndUpdate(it, env.executor.id)
        } ?: false
      } != null

      if (!isCompoundProcess) {
        if (getMoveNewOnTop(env)) {
          moveToTop(slot.id)
        }
      }
    }
  }

  fun processTerminating(env: ExecutionEnvironment) {
    slotsData.values.firstOrNull { it.environment?.executionId == env.executionId }?.let {
      it.environment = env
    }
    activeProcesses.updateActiveProcesses(slotsData)
    updateState()
    SwingUtilities.invokeLater {
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

    SwingUtilities.invokeLater {
      ActivityTracker.getInstance().inc()
    }
  }

  internal fun addAndSaveSlot(): SlotDate {
    val slot = addSlot()
    saveSlotsConfiguration()
    return slot
  }

  private fun addSlot(id: String = UUID.randomUUID().toString()): SlotDate {
    val slot = SlotDate(id)
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

      SwingUtilities.invokeLater {
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
            Messages.getQuestionIcon()/*, object : DialogWrapper.DoNotAskOption.Adapter() {
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
    AppUIUtil.invokeLaterIfProjectAlive(project) {
      project.messageBus.syncPublisher(RUN_TOOLBAR_SLOT_CONFIGURATION_MAP_TOPIC).configurationChanged(slotId, configuration)
    }
    saveSlotsConfiguration()
  }

  private fun saveSlotsConfiguration() {
    if (IS_RUN_MANAGER_INITIALIZED.get(project) == true) {
      val runManager = RunManager.getInstance(project)
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
    if (RunToolbarProcess.logNeeded) LOG.info("MANAGER saveSlotsConfiguration: ${configurations} RunToolbar")

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

  fun getConfigurationMap(): Map<String, RunnerAndConfigurationSettings?> {
    return getConfigurationMap(getSlotOrder())
  }

  private fun publishConfigurations(slotConfigurations: Map<String, RunnerAndConfigurationSettings?>) {
    AppUIUtil.invokeLaterIfProjectAlive(project) {
      project.messageBus.syncPublisher(RUN_TOOLBAR_SLOT_CONFIGURATION_MAP_TOPIC).slotsConfigurationChanged(slotConfigurations)
    }
  }
}

internal open class SlotDate(override var id: String) : RunToolbarData {
  companion object {
    var index = 0
  }

  fun updateId(value: String) {
    id = value
  }

  override var configuration: RunnerAndConfigurationSettings? = null
    get() = environment?.runnerAndConfigurationSettings ?: field

  override var environment: ExecutionEnvironment? = null
    set(value) {
      if (field != value)
        field = value
      value?.let {
        configuration = it.runnerAndConfigurationSettings
      } ?: run {
        waitingForAProcesses.clear()
      }
    }

  override val waitingForAProcesses = RWWaitingForAProcesses()

  override fun clear() {
    environment = null
    waitingForAProcesses.clear()
  }

  override fun toString(): String {
    return "$id-${environment?.let { "$it [${it.executor.actionName} ${it.executionId}]" } ?: configuration?.configuration?.name ?: "configuration null"}"
  }
}
