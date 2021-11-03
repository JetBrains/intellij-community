// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.CommonBundle
import com.intellij.execution.*
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.ActivityTracker
import com.intellij.lang.LangBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import java.util.function.Function
import javax.swing.SwingUtilities

class RunToolbarSlotManager(val project: Project) {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarSlotManager::class.java)
    fun getInstance(project: Project): RunToolbarSlotManager = project.service()
  }

  private val runToolbarSettings = RunToolbarSettings.getInstance(project)
  private var connection: MessageBusConnection? = null

  private val slotListeners = mutableListOf<SlotListener>()
  internal fun addListener(listener: SlotListener) {
    slotListeners.add(listener)
  }

  internal fun removeListener(listener: SlotListener) {
    slotListeners.remove(listener)
  }

  private val listeners = mutableListOf<ActiveListener>()
  internal fun addListener(listener: ActiveListener) {
    listeners.add(listener)
  }

  internal fun removeListener(listener: ActiveListener) {
    listeners.remove(listener)
  }

  private val stateListeners = mutableListOf<StateListener>()
  internal fun addListener(listener: StateListener) {
    stateListeners.add(listener)
  }

  internal fun removeListener(listener: StateListener) {
    stateListeners.remove(listener)
  }

  enum class State {
    MULTIPLE,
    MULTIPLE_WITH_MAIN,
    SINGLE_MAIN,
    SINGLE_PLAIN,
    INACTIVE;

    fun isSingleProcess(): Boolean {
      return this == SINGLE_PLAIN || this == SINGLE_MAIN
    }

    fun isSingleMain(): Boolean {
      return this == SINGLE_MAIN
    }

    fun isSinglePlain(): Boolean {
      return this == SINGLE_PLAIN
    }

    fun isMultipleProcesses(): Boolean {
      return this == MULTIPLE || this == MULTIPLE_WITH_MAIN
    }

    fun isMainActive(): Boolean {
      return this == MULTIPLE_WITH_MAIN || this == SINGLE_MAIN
    }

    fun isActive(): Boolean {
      return this != INACTIVE
    }
  }

  internal var active: Boolean = false
    set(value) {
      if(field == value) return

      field = value

      if(value) {
        if(RunToolbarProcess.logNeeded) LOG.info("SLOT MANAGER settings: new on top ${getMoveNewOnTop()}; update by selected ${getUpdateMainBySelected()} RunToolbar" )

        val runConfigurations = runToolbarSettings.getRunConfigurations()
        runConfigurations.forEachIndexed { index, entry ->
          if(index == 0) {
            mainSlotData.configuration = entry
          } else {
            addSlot().configuration = entry
          }
        }
        if(RunToolbarProcess.logNeeded) LOG.info("SLOT MANAGER restoreRunConfigurations: $runConfigurations RunToolbar" )

        val con = project.messageBus.connect()
        connection = con

        con.subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
          override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
            if (!getUpdateMainBySelected() || mainSlotData.configuration == settings) return

            mainSlotData.environment?.let {
              val slot = addSlot()
              slot.configuration = settings
              if(RunToolbarProcess.logNeeded) LOG.info("SLOT MANAGER runConfigurationSelected: $settings first slot added RunToolbar" )
              moveToTop(slot.id)
            } ?: kotlin.run {
              mainSlotData.configuration = settings
              if(RunToolbarProcess.logNeeded) LOG.info("SLOT MANAGER runConfigurationSelected: $settings change main configuration RunToolbar" )
              update()
            }
          }
        })

        listeners.forEach { it.enabled() }
      } else {
        listeners.forEach { it.disabled()  }
        clear()
      }

      slotListeners.forEach { it.rebuildPopup() }
    }

  private fun getUpdateMainBySelected(): Boolean {
    return runToolbarSettings.getUpdateMainBySelected()
  }

  private fun getMoveNewOnTop(): Boolean {
    return runToolbarSettings.getMoveNewOnTop()
  }

  private fun clear() {
    connection?.let {
      it.disconnect()
      connection = null
    }
    mainSlotData.clear()
    dataIds.clear()
    slotsData.clear()
    slotsData[mainSlotData.id] = mainSlotData

    activeProcesses.clear()
  }

  internal var mainSlotData = SlotDate()

  val activeProcesses = ActiveProcesses()
  private val dataIds = mutableListOf<String>()

  private val slotsData = mutableMapOf<String, SlotDate>()


  private fun traceState() {
    if(!RunToolbarProcess.logNeeded) return

    val separator = " "
    val ids = dataIds.indices.mapNotNull { "${it+1}: ${slotsData[dataIds[it]]}" }.joinToString(", ")
    LOG.info("SLOT MANAGER state: $state" +
             "${separator}== slots: 0: ${mainSlotData}, $ids" +
             "${separator}== slotsData: ${slotsData.values} RunToolbar")
  }


  init {
    SwingUtilities.invokeLater {
      if (project.isDisposed) return@invokeLater

      slotsData[mainSlotData.id] = mainSlotData

      addListener(RunToolbarShortcutHelper(project))

      Disposer.register(project) {
        connection?.disconnect()
        listeners.clear()
        stateListeners.clear()
        slotListeners.clear()
      }
    }
  }

  private fun update() {
    saveSlotsConfiguration()
    updateState()
  }

  internal fun getMainOrFirstActiveProcess(): RunToolbarProcess? {
    return mainSlotData.environment?.getRunToolbarProcess() ?: activeProcesses.processes.keys.firstOrNull()
  }

  internal fun slotsCount(): Int {
    return dataIds.size
  }

  private var state: State = State.INACTIVE
    set(value) {
      if (value == field) return
      field = value
      if(RunToolbarProcess.logNeeded) LOG.info("MANAGER STATE $value RunToolbar" )
      traceState()
      stateListeners.forEach { it.stateChanged(value) }
    }

  private fun updateState() {
    state = when (activeProcesses.getEnvironmentCount()) {
      0 -> State.INACTIVE
      1 -> {
        mainSlotData.environment?.let {
          State.SINGLE_MAIN
        } ?: State.SINGLE_PLAIN
      }
      else -> {
        mainSlotData.environment?.let {
          State.MULTIPLE_WITH_MAIN
        } ?: State.MULTIPLE
      }
    }
  }

  internal fun getState(): State {
    return state
  }

  internal fun processStarted(env: ExecutionEnvironment) {
    if (getMoveNewOnTop()) {
      addNewProcessOnTop(env)
    }
    else {
      addNewProcessOnBottom(env)
    }

    SwingUtilities.invokeLater {
      ActivityTracker.getInstance().inc()
    }
  }

  private fun addNewProcessOnTop(env: ExecutionEnvironment) {
    val appropriateSettings = slotsData.values.map { it }.filter { it.configuration == env.runnerAndConfigurationSettings }
    val emptySlotsWithConfiguration = appropriateSettings.filter { it.environment == null }

    var newSlot = false
    val slot = appropriateSettings.firstOrNull { it.environment?.executionId == env.executionId }
               ?: emptySlotsWithConfiguration.firstOrNull {
                 it.waitingForProcess.contains(env.executor.id)
               }
               ?: emptySlotsWithConfiguration.firstOrNull()
               ?: kotlin.run {
                 newSlot = true
                 addSlot()
               }

    slot.environment = env
    slot.waitingForProcess.clear()
    if(RunToolbarProcess.logNeeded) LOG.info("MANAGER process added on top: $env (${env.executionId}) RunToolbar" )

    activeProcesses.updateActiveProcesses(slotsData)
    if (newSlot) {
      moveToTop(slot.id)
    }
    else {
      update()
    }
  }

  private fun addNewProcessOnBottom(env: ExecutionEnvironment) {
    val appropriateSettings = slotsData.values.map { it }.filter { it.configuration == env.runnerAndConfigurationSettings }
    val emptySlotsWithConfiguration = appropriateSettings.filter { it.environment == null }

    val slot = appropriateSettings.firstOrNull { it.environment?.executionId == env.executionId }
               ?: emptySlotsWithConfiguration.firstOrNull {
                 it.waitingForProcess.contains(env.executor.id)
               }
               ?: emptySlotsWithConfiguration.firstOrNull()
               ?: kotlin.run {
                 addAndSaveSlot()
               }

    slot.environment = env
    slot.waitingForProcess.clear()
    if(RunToolbarProcess.logNeeded) LOG.info("MANAGER process added bottom: $env (${env.executionId}) RunToolbar" )

    activeProcesses.updateActiveProcesses(slotsData)
    update()
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
        /*it.isTemporary ||*/ !RunManager.getInstance(project).hasSettings(it)
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

    if(RunToolbarProcess.logNeeded) LOG.info( "SLOT MANAGER process stopped: $executionId RunToolbar" )
    activeProcesses.updateActiveProcesses(slotsData)
    updateState()

    SwingUtilities.invokeLater {
      ActivityTracker.getInstance().inc()
    }
  }

  fun extraSlotCount(): Int {
    return dataIds.size
  }

  internal fun addAndSaveSlot(): SlotDate {
    val slot = addSlot()
    saveSlotsConfiguration()
    return slot
  }

  private fun addSlot(): SlotDate {
    val slot = SlotDate()
    dataIds.add(slot.id)
    slotsData[slot.id] = slot

    slotListeners.forEach{ it.slotAdded() }

    return slot
  }

  internal fun getData(index: Int): SlotDate? {
    return if(index >= 0 && index < dataIds.size) {
      dataIds[index].let {
        slotsData[it]
      }
    } else null
  }


  internal fun moveToTop(id: String) {
    if(mainSlotData.id == id) return

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
        slotListeners.forEach { it.slotRemoved(index) }
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
    } ?: slotListeners.forEach { it.rebuildPopup() }

    update()
  }

  internal fun saveSlotsConfiguration() {
    val list = mutableListOf<String>()
    list.add(mainSlotData.id)
    list.addAll(dataIds)

    if (IS_RUN_MANAGER_INITIALIZED.get(project) == true) {
      val runManager = RunManager.getInstance(project)
      if (mainSlotData.configuration !=null && mainSlotData.configuration != runManager.selectedConfiguration && mainSlotData.environment?.getRunToolbarProcess()?.isTemporaryProcess() != true) {
        runManager.selectedConfiguration = mainSlotData.configuration
        if(RunToolbarProcess.logNeeded) LOG.info("MANAGER saveSlotsConfiguration. change selected configuration by main: ${mainSlotData.configuration} RunToolbar" )
      }
    }

    val configurtions = list.mapNotNull { slotsData[it]?.configuration }.toMutableList()
    if(RunToolbarProcess.logNeeded) LOG.info("MANAGER saveSlotsConfiguration: ${configurtions} RunToolbar" )
    runToolbarSettings.setRunConfigurations(configurtions)
  }
}

class ActiveProcesses {
  internal var activeSlots = mutableListOf<SlotDate>()
  val processes = mutableMapOf<RunToolbarProcess, MutableList<ExecutionEnvironment>>()
  private var activeCount = 0

  fun getEnvironmentCount(): Int = activeCount

  fun getText(): String? {
    return when {
      activeCount == 1 -> {
        processes.entries.firstOrNull()?. let { entry ->
          entry.value.firstOrNull()?.contentToReuse?.let {
            ExecutionBundle.message("run.toolbar.started", entry.key.name, it.displayName)
          }
        }
      }
      activeCount > 1 -> { processes.map { ExecutionBundle.message("run.toolbar.started", it.key.name, it.value.size) }.joinToString ("  " ) }

      else -> null
    }

  }

  internal fun updateActiveProcesses(slotsData: MutableMap<String, SlotDate>) {
    processes.clear()
    val list = slotsData.values.filter { it.environment != null }.toMutableList()
    activeSlots = list
    list.mapNotNull { it.environment }.forEach{ environment ->
      environment.getRunToolbarProcess()?.let {
        processes.computeIfAbsent(it, Function { mutableListOf() }).add(environment)
      }
    }

    activeCount = processes.values.map { it.size }.sum()
  }

  internal fun clear() {
    activeCount = 0
    processes.clear()
  }
}

internal open class SlotDate(override val id: String = "slt${index++}") : RunToolbarData {
  companion object {
    var index = 0
  }
  override var configuration: RunnerAndConfigurationSettings? =  null
  override var environment: ExecutionEnvironment? = null
    set(value) {
      if (field != value)
        field = value
      value?.let {
        configuration = it.runnerAndConfigurationSettings
      }
    }
  override val waitingForProcess: MutableSet<String> = mutableSetOf()

  internal fun clear() {
    environment = null
    waitingForProcess.clear()
  }

  override fun toString(): String {
    return "($id-${environment?.let{"$it(${it.executor.actionName} ${it.executionId})"} ?: configuration?.configuration?.name ?: "configuration null"})"
  }
}

internal interface SlotListener {
  fun slotAdded()
  fun slotRemoved(index: Int)
  fun rebuildPopup()
}

internal interface ActiveListener {
  fun enabled()
  fun disabled() {}
}

internal interface StateListener {
  fun stateChanged(state: RunToolbarSlotManager.State)
}