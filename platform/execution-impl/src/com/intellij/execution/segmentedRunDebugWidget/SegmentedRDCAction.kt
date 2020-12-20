// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.PillActionComponent
import com.intellij.openapi.project.DumbAware
import javax.swing.SwingUtilities

class SegmentedRDCAction : PillActionComponent(), DumbAware {
  init {
    ActionManager.getInstance().getAction("SegmentedRunDebugConfigActionGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.project?.let {
      RunDebugConfigManager.getInstance(it)?.let { debugConfigManager ->
        if(debugConfigManager.getState() != RunDebugConfigManager.State.DEFAULT) showPill() else hidePill()
      }
    }
  }
}