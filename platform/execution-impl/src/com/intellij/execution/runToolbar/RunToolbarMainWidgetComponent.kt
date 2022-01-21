// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.RunToolbarSlotManager.State
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.SwingUtilities

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

  private val managerStateListener = object : StateListener {
    override fun stateChanged(state: State) {
      updateState()
    }
  }

  private var state: RunToolbarMainSlotState? = null

  private fun updateState() {
    state = project?.let {
      val slotManager = RunToolbarSlotManager.getInstance(it)
      val value = when (slotManager.getState()) {
        State.SINGLE_MAIN -> {
          RunToolbarMainSlotState.PROCESS
        }
        State.SINGLE_PLAIN,
        State.MULTIPLE -> {
          if(isOpened) RunToolbarMainSlotState.CONFIGURATION else RunToolbarMainSlotState.INFO
        }
        State.INACTIVE -> {
          RunToolbarMainSlotState.CONFIGURATION
        }
        State.MULTIPLE_WITH_MAIN -> {
          if(isOpened) RunToolbarMainSlotState.PROCESS else RunToolbarMainSlotState.INFO
        }
      }

      value
    }

    if(RunToolbarProcess.logNeeded) LOG.info("MAIN SLOT state updated: $state RunToolbar")
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

    (SwingUtilities.getWindowAncestor(this) as? IdeFrame)?.project?.let {
      project = it
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
    if (value == 1) {
      slotManager.active = true
    }

    DataManager.registerDataProvider(component, DataProvider { key ->
      when {
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

    rebuildPopupControllerComponent()
    addContainerListener(componentListener)
    slotManager.addListener(managerStateListener)
    updateState()
  }

  private fun remove(project: Project) {
    RunToolbarSlotManager.getInstance(project).removeListener(managerStateListener)
    counter[project]?.let {
      val value = maxOf(it - 1, 0)
      counter[project] = value
      if (value == 0) {
        RunToolbarSlotManager.getInstance(project).active = false
        counter.remove(project)
      }
    }

    removeContainerListener(componentListener)
    popupController?.let {
      if(!Disposer.isDisposed(it)) {
        Disposer.dispose(it)
      }
    }
  }
}

