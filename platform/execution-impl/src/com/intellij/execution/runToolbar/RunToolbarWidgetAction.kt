// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import com.intellij.openapi.util.registry.Registry
import javax.swing.SwingUtilities

class RunToolbarWidgetAction : SegmentedBarActionComponent(ActionPlaces.RUN_TOOLBAR) {
  companion object {
    const val runDebugKey = "ide.new.navbar"

  }

  private fun isRunToolbar(): Boolean {
    return Registry.get(runDebugKey).asBoolean()
  }

  init {
    ActionManager.getInstance().getAction("RunToolbarMainActionsGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isRunToolbar()
    e.presentation.putClientProperty(RunToolbarMainWidgetComponent.PROP_POJECT, e.project)
  }

  override fun createSegmentedActionToolbar(presentation: Presentation, place: String, group: ActionGroup): SegmentedActionToolbarComponent {
    val component = RunToolbarMainWidgetComponent(presentation, place, group)
    component.targetComponent = component

    return component
  }
}