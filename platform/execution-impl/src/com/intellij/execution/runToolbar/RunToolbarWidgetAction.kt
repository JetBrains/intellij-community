// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent

class RunToolbarWidgetAction : SegmentedBarActionComponent() {

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
}