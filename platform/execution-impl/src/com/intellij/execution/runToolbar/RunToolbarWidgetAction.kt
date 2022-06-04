// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import javax.swing.BorderFactory
import javax.swing.JComponent

class RunToolbarWidgetAction : SegmentedBarActionComponent() {
  val TOOLBAR_GAP = 4

  init {
    ActionManager.getInstance().getAction("RunToolbarMainActionsGroup")?.let {
      if (it is ActionGroup) {
        actionGroup = it
      }
    }
  }

  override fun createSegmentedActionToolbar(presentation: Presentation,
                                            place: String,
                                            group: ActionGroup): SegmentedActionToolbarComponent {
    val component = RunToolbarMainWidgetComponent(presentation, place, group)
    component.targetComponent = component

    return component
  }
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply {
      border = BorderFactory.createEmptyBorder(0, TOOLBAR_GAP, 0, TOOLBAR_GAP)
    }
  }
}