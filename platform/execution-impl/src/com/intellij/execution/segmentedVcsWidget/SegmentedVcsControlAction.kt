// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedVcsWidget // Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class SegmentedVcsControlAction : SegmentedBarActionComponent(ActionPlaces.NEW_TOOLBAR) {
  init {
    ActionManager.getInstance().getAction("SegmentedVcsActionsBarGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
    paintBorderAroundOneItem = false
  }

  override fun update(e: @NotNull AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = actionGroup != null
  }
  override fun createCustomComponent(presentation: Presentation, place_: String): JComponent {
    return JPanel(MigLayout("novisualpadding, ins 1 2 1 2")).apply{
      add(super.createCustomComponent(presentation, place_), "gap 0")
    }
  }
}