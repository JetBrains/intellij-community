// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.data.RWSlotManagerState
import com.intellij.execution.runToolbar.data.RWStateListener
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener

class RunToolbarMainWidgetComponent(val presentation: Presentation, place: String, group: ActionGroup) :
  FixWidthSegmentedActionToolbarComponent(place, group) {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarMainWidgetComponent::class.java)
    private var counter: MutableMap<Project, Int> = mutableMapOf()
  }

  override fun logNeeded(): Boolean = RunToolbarProcess.logNeeded

  private var project: Project? = null
    set(value) {
      if(field == value) return
      field?.let {
        remove(it)
      }

      field = value

      field?.let {
        add(it)
      }
    }

  private var popupController: RunToolbarPopupController? = null

  private val componentListener = object : ContainerListener {
    override fun componentAdded(e: ContainerEvent) {
      rebuildPopupControllerComponent()
    }

    override fun componentRemoved(e: ContainerEvent) {
      rebuildPopupControllerComponent()
    }
  }

  private val managerStateListener = object : RWStateListener {
    override fun stateChanged(state: RWSlotManagerState) {
      updateState()
      this@RunToolbarMainWidgetComponent.updateActionsImmediately(true)
    }
  }

  private var state: RunToolbarMainSlotState? = null

  private fun updateState() {
    state = project?.let {
      val slotManager = RunToolbarSlotManager.getInstance(it)
      val value = when (slotManager.getState()) {
        RWSlotManagerState.SINGLE_MAIN -> {
          RunToolbarMainSlotState.PROCESS
        }
        RWSlotManagerState.SINGLE_PLAIN,
        RWSlotManagerState.MULTIPLE -> {
          if(isOpened) RunToolbarMainSlotState.CONFIGURATION else RunToolbarMainSlotState.INFO
        }
        RWSlotManagerState.INACTIVE -> {
          RunToolbarMainSlotState.CONFIGURATION
        }
        RWSlotManagerState.MULTIPLE_WITH_MAIN -> {
          if(isOpened) RunToolbarMainSlotState.PROCESS else RunToolbarMainSlotState.INFO
        }
      }

      value
    }

    if(RunToolbarProcess.logNeeded) LOG.info("MAIN SLOT state updated: $state RunToolbar")
  }

  override fun traceState(lastIds: List<String>, filteredIds: List<String>, ides: List<String>) {
    if(logNeeded() && filteredIds != lastIds ) LOG.info("MAIN SLOT state: ${state} new filtered: ${filteredIds}} visible: $ides RunToolbar")
  }

  internal var isOpened = false
    set(value) {
      if(field == value) return

      field = value
      if(RunToolbarProcess.logNeeded) LOG.info("MAIN SLOT isOpened: $isOpened RunToolbar")
      updateState()

      if (RunToolbarProcess.isExperimentalUpdatingEnabled) {
        forceUpdate()
      }
    }

  override fun isSuitableAction(action: AnAction): Boolean {
    return state?.let {
      if(action is RTBarAction) {
        action.checkMainSlotVisibility(it)
      } else true
    } ?: true
  }

  override fun addNotify() {
    super.addNotify()

    CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let {
      project = it

      RunWidgetWidthHelper.getInstance(it).runConfig = RunToolbarSettings.getInstance(it).getRunConfigWidth()
    }
  }

  override fun updateWidthHandler() {
    super.updateWidthHandler()
    project?.let {
      RunToolbarSettings.getInstance(it).setRunConfigWidth(RunWidgetWidthHelper.getInstance(it).runConfig)
    }
  }

  private fun rebuildPopupControllerComponent() {
    popupController?.let {
      it.updateControllerComponents(components.filter{it is PopupControllerComponent}.toMutableList())
    }
  }

  override fun removeNotify() {
    project = null
    super.removeNotify()
  }

  private fun add(project: Project) {
    popupController = RunToolbarPopupController(project, this)

    val value = counter.getOrDefault(project, 0) + 1
    counter[project] = value
    val slotManager = RunToolbarSlotManager.getInstance(project)
    DataManager.registerDataProvider(component, DataProvider { key ->
      when {
        RunToolbarProcessData.RW_SLOT.`is`(key) -> {
          slotManager.mainSlotData.id
        }
        RunToolbarData.RUN_TOOLBAR_DATA_KEY.`is`(key) -> {
          slotManager.mainSlotData
        }
        RunToolbarData.RUN_TOOLBAR_POPUP_STATE_KEY.`is`(key) -> {
          isOpened
        }
        RunToolbarData.RUN_TOOLBAR_MAIN_STATE.`is`(key) -> {
          state
        }
        else -> null
      }
    })

    if (value == 1) {
      slotManager.stateListeners.addListener(managerStateListener)
      slotManager.active = true
    }

    rebuildPopupControllerComponent()
    addContainerListener(componentListener)
  }

  private fun remove(project: Project) {
    val slotManager = if (!project.isDisposed) RunToolbarSlotManager.getInstance(project) else null
    slotManager?.stateListeners?.removeListener(managerStateListener)

    counter[project]?.let {
      val value = maxOf(it - 1, 0)
      counter[project] = value
      if (value == 0) {
        slotManager?.active = false
        counter.remove(project)
      }
    }

    removeContainerListener(componentListener)
    popupController?.let {
      if(!Disposer.isDisposed(it)) {
        Disposer.dispose(it)
      }
    }
    popupController = null
    state = null
  }
}

