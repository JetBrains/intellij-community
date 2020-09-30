// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import javax.swing.SwingUtilities

class RDCBarAction : SegmentedBarActionComponent() {
  init {
    ActionManager.getInstance().getAction("RunDebugConfigActionBarGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
  }
}