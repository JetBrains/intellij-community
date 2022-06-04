// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedVcsWidget // Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import org.jetbrains.annotations.NotNull
import javax.swing.BorderFactory
import javax.swing.JComponent

class SegmentedVcsControlAction : SegmentedBarActionComponent() {
  val TOOLBAR_GAP = 4

  init {
    ActionManager.getInstance().getAction("SegmentedVcsActionsBarGroup")?.let {
      if (it is ActionGroup) {
        actionGroup = it
      }
    }
  }

  override fun update(e: @NotNull AnActionEvent) {
    if (e.place !== ActionPlaces.MAIN_TOOLBAR) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
    e.presentation.isVisible = actionGroup != null
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