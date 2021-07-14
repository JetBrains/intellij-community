// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.CommonBundle
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import java.util.*
import java.util.function.Function
import javax.swing.SwingUtilities

class RunToolbarSlotManager(val project: Project) {
  companion object {
    fun getInstance(project: Project): RunToolbarSlotManager = project.service()
  }

  private val listeners = mutableListOf<SlotListener>()
  internal fun addListener(listener: SlotListener) {
    listeners.add(listener)
  }

  internal fun removeListener(listener: SlotListener) {
    listeners.remove(listener)
  }

  enum class State {
    MULTIPLE,
    SINGLE_MAIN,
    SINGLE_PLAIN,
    INACTIVE;

    fun isSingleProcess(): Boolean {
      return this == SINGLE_PLAIN || this == SINGLE_MAIN
    }

    fun isSingleMain(): Boolean {
      return this == SINGLE_MAIN
    }

    fun isMultipleProcesses(): Boolean {
      return this == MULTIPLE
    }

    fun isActive(): Boolean {
      return this != INACTIVE
    }
  }

  internal val mainSlotData = MainSlotData()

  val activeProcesses = ActiveProcesses()
  private val dataIds = mutableListOf<String>()

  private val slotsData = mutableMapOf<String, SlotDate>()

  init {
    ApplicationManager.getApplication().invokeLater {
      slotsData[mainSlotData.id] = mainSlotData
    }
  }

  internal fun slotsCount(): Int {
    return dataIds.size
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
                 addNewSlot()
               }

    slot.environment = env
    slot.waitingForProcess.clear()

    activeProcesses.updateActiveProcesses(slotsData)
  }

  fun getState(): State {
    return when(activeProcesses.getEnvironmentCount()) {
      0 -> State.INACTIVE
      1 -> { mainSlotData.environment?.let {
        State.SINGLE_MAIN
       } ?: State.SINGLE_PLAIN
      }
      else -> State.MULTIPLE
    }
  }

  fun processStopped(executionId: Long) {
    slotsData.values.firstOrNull { it.environment?.executionId == executionId }?.environment = null

    activeProcesses.updateActiveProcesses(slotsData)
  }


  fun extraSlotCount(): Int {
    return dataIds.size
  }

  internal fun addNewSlot(): SlotDate {
    val slot = SlotDate()
    dataIds.add(slot.id)
    slotsData[slot.id] = slot

    listeners.forEach{ it.slotAdded() }
    return slot
  }

  internal fun getData(index: Int): SlotDate? {
    return if(index >= 0 && index < dataIds.size) {
      dataIds[index].let {
        slotsData[it]
      }
    } else null
  }

  internal fun removeSlot(id: String) {
    val index = dataIds.indexOf(id)

    if(index >= 0) {
      fun remove() {

        slotsData.remove(id)
        dataIds.remove(id)

        SwingUtilities.invokeLater {
          listeners.forEach { it.slotRemoved(index) }
        }
      }

      getData(index)?.let { slotDate ->
        slotDate.environment?.let {
          if (Messages.showOkCancelDialog(
              project,
              "qwerty",
              "qwerty",
              "qwerty",
              CommonBundle.getCancelButtonText(),
              Messages.getQuestionIcon(), object : DialogWrapper.DoNotAskOption.Adapter() {
              override fun rememberChoice(isSelected: Boolean, exitCode: Int) {

              }
            }) == Messages.OK) {
            it.contentToReuse?.let {
              ExecutionManagerImpl.stopProcess(it)
            }

            remove()
          }
        } ?: run {
          remove()
        }
      } ?: listeners.forEach { it.rebuildPopup() }
    } else {
      listeners.forEach { it.rebuildPopup() }
    }
  }
}

class ActiveProcesses {
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
    slotsData.values.mapNotNull { it.environment }.forEach{ environment ->
      environment.getRunToolbarProcess()?.let {
        processes.computeIfAbsent(it, Function { mutableListOf() }).add(environment)
      }
    }

    activeCount = processes.values.map { it.size }.sum()
  }
}

internal open class SlotDate(override val id: String = UUID.randomUUID().toString()) : RunToolbarData {
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
}

internal class MainSlotData(override val id: String = UUID.randomUUID().toString()) : SlotDate(id) {
  companion object{
    var RUN_TOOLBAR_IS_EXTRA_SLOTS_OPENED: DataKey<() -> Boolean> = DataKey.create("RUN_TOOLBAR_IS_EXTRA_SLOTS_OPENED")
  }
}

internal interface SlotListener {
  fun slotAdded()
  fun slotRemoved(index: Int)
  fun rebuildPopup()
}