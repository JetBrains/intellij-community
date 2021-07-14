// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

class RunToolbarMainWidgetComponent(val presentation: Presentation, place: String, group: ActionGroup) :
  FixWidthSegmentedActionToolbarComponent(place, group) {
  companion object {
    var RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY: DataKey<RunToolbarMainWidgetComponent> = DataKey.create(
      "RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY")
    internal val PROP_POJECT = Key<Project>("PROP_POJECT")
  }

  init {
    presentation.addPropertyChangeListener(
      PropertyChangeListener { evt: PropertyChangeEvent -> presentationChanged(evt) })
  }

  internal var isOpened = false
    set(value) {
      field = value
      //updateActionsImmediately(true)
    }

  private fun presentationChanged(event: PropertyChangeEvent) {
    presentation.getClientProperty(PROP_POJECT)?.let { project ->
      DataManager.registerDataProvider(component, DataProvider { key ->
        when {
          RunToolbarData.RUN_TOOLBAR_DATA_KEY.`is`(key) -> {
            RunToolbarSlotManager.getInstance(project).mainSlotData
          }
          RunToolbarData.RUN_TOOLBAR_POPUP_STATE_KEY.`is`(key) -> {
            isOpened
          }
          RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY.`is`(key) -> {
            this@RunToolbarMainWidgetComponent
          }
          else -> null
        }
      })
    }
  }
}