// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.SwingUtilities

class RunToolbarMainWidgetComponent(val presentation: Presentation, place: String, group: ActionGroup) :
  FixWidthSegmentedActionToolbarComponent(place, group) {
  companion object {
    private var counter: MutableMap<Project, Int> = mutableMapOf()
  }

  private var project: Project? = null
  private var popupController: RunToolbarPopupController? = null

  private val componentListener = object : ContainerListener {
    override fun componentAdded(e: ContainerEvent) {
      rebuildPopupControllerComponent()
    }

    override fun componentRemoved(e: ContainerEvent) {
      rebuildPopupControllerComponent()
    }
  }

  internal var isOpened = false
    set(value) {
      field = value
      //updateActionsImmediately(true)
    }

  override fun addNotify() {
    super.addNotify()

    (SwingUtilities.getWindowAncestor(this) as? IdeFrame)?.project?.let {
      project = it
      popupController = RunToolbarPopupController(it, this)

      val value = counter.getOrDefault(it, 0) + 1
      counter[it] = value
      if (value == 1) {
        RunToolbarSlotManager.getInstance(it).active = true
      }

      DataManager.registerDataProvider(component, DataProvider { key ->
        when {
          RunToolbarData.RUN_TOOLBAR_DATA_KEY.`is`(key) -> {
            RunToolbarSlotManager.getInstance(it).mainSlotData
          }
          RunToolbarData.RUN_TOOLBAR_POPUP_STATE_KEY.`is`(key) -> {
            isOpened
          }
          else -> null
        }
      })
    }

    rebuildPopupControllerComponent()
    addContainerListener(componentListener)
  }

  private fun rebuildPopupControllerComponent() {
    popupController?.let {
      it.updateControllerComponents(components.filter{it is PopupControllerComponent}.toMutableList())
    }
  }

  override fun removeNotify() {
    project?.let { project ->
      counter[project]?.let {
        val value = maxOf(it - 1, 0)
        counter[project] = value
        if (value == 0) {
          RunToolbarSlotManager.getInstance(project).active = false
        }
      }
    }
    removeContainerListener(componentListener)
    popupController?.let {
      if(!Disposer.isDisposed(it)) {
        Disposer.dispose(it)
      }
    }

    super.removeNotify()
  }


}