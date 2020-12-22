// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedVcsWidget // Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import org.jetbrains.annotations.NotNull
import javax.swing.SwingUtilities

class SegmentedVcsControlAction : SegmentedBarActionComponent() {
  init {
    ActionManager.getInstance().getAction("SegmentedVcsActionsBarGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
  }

  override fun update(e: @NotNull AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = actionGroup != null
  }
}