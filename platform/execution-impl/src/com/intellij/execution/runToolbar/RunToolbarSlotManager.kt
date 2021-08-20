// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.CommonBundle
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.lang.LangBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.util.function.Function
import javax.swing.SwingUtilities

class RunToolbarSlotManager(val project: Project) {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarSlotManager::class.java)
    fun getInstance(project: Project): RunToolbarSlotManager = project.service()
  }

  private val runToolbarSettings = RunToolbarSettings.getInstance(project)

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
        val runConfigurations = runToolbarSettings.getRunConfigurations()
        runConfigurations.forEachIndexed { index, entry ->
          if(index == 0) {
            mainSlotData.configuration = entry
          } else {
            addSlot().configuration = entry
          }
        }
        listeners.forEach { it.enabled() }
      } else {
        listeners.forEach { it.disabled()  }
        clear()
      }

      slotListeners.forEach { it.rebuildPopup() }
    }

  private fun clear() {
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
    val separator = " "
    val ids = dataIds.indices.mapNotNull { "${it+1}: ${slotsData[dataIds[it]]}" }.joinToString(", ")
    LOG.info("state: $state" +
             "${separator}== slots: 0: ${mainSlotData}, $ids" +
             "${separator}== slotData: ${slotsData.values}")
  }


  init {
    SwingUtilities.invokeLater {
      if(project.isDisposed) return@invokeLater

      slotsData[mainSlotData.id] = mainSlotData

      addListener(RunToolbarShortcutHelper(project))
    }
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
      LOG.info("state updated $value")
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

  fun getState(): State {
    return state
  }


  fun processStarted(env: ExecutionEnvironment) {
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
    LOG.info("process started: $env (${env.executionId}) ")

    activeProcesses.updateActiveProcesses(slotsData)
    updateState()
  }

  fun processStopped(executionId: Long) {
    slotsData.values.firstOrNull { it.environment?.executionId == executionId }?.environment = null
    LOG.info("process stopped: $executionId ")
    activeProcesses.updateActiveProcesses(slotsData)
    updateState()
  }

  fun extraSlotCount(): Int {
    return dataIds.size
  }

  internal fun addAndSaveSlot(): SlotDate {
    val slot = addSlot()
    saveData()
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

    updateState()
    saveData()
  }

  internal fun removeSlot(id: String) {
    val index = dataIds.indexOf(id)

    if(index >= 0) {
      fun remove() {

        slotsData.remove(id)
        dataIds.remove(id)

        SwingUtilities.invokeLater {
          slotListeners.forEach { it.slotRemoved(index) }
        }
      }

      getData(index)?.let { slotDate ->
        slotDate.environment?.let {
          if (Messages.showOkCancelDialog(
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
    } else {
      slotListeners.forEach { it.rebuildPopup() }
    }

    saveData()
  }

  fun saveData() {
    val list = mutableListOf<String>()
    list.add(mainSlotData.id)
    list.addAll(dataIds)

    runToolbarSettings.setRunConfigurations( list.mapNotNull{ slotsData[it]?.configuration }.toMutableList())
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
          entry.value.firstOrNull()?.runnerAndConfigurationSettings?.let {
            ExecutionBundle.message("run.toolbar.started", entry.key.name,  Executor.shortenNameIfNeeded(it.name))
          }
        }
      }
      activeCount > 1 -> { processes.map { ExecutionBundle.message("run.toolbar.started", it.key.name, it.value.size) }.joinToString ("  " ) }

      else -> null
    }

  }

  internal fun updateActiveProcesses(slotsData: MutableMap<String, SlotDate>) {
    processes.clear()
    val list = slotsData.values.filter{it.environment != null}.toMutableList()
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