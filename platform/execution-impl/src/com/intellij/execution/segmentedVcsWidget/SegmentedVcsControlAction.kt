// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.segmentedVcsWidget

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import javax.swing.BorderFactory
import javax.swing.JComponent

private const val TOOLBAR_GAP = 4

internal class SegmentedVcsControlAction : SegmentedBarActionComponent() {


  init {
    ActionManager.getInstance().getAction("SegmentedVcsActionsBarGroup")?.let {
      if (it is ActionGroup) {
        actionGroup = it
      }
    }
  }

  override fun update(e: AnActionEvent) {
    if (e.place !== ActionPlaces.MAIN_TOOLBAR) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }

  override fun createSegmentedActionToolbar(presentation: Presentation,
                                            place: String,
                                            group: ActionGroup): SegmentedActionToolbarComponent {
    return SegmentedActionToolbarComponent(place, group, false)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply {
      border = BorderFactory.createEmptyBorder(0, TOOLBAR_GAP, 0, TOOLBAR_GAP)
    }
  }
}