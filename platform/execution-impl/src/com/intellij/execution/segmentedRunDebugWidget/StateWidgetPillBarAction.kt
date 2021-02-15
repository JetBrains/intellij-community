// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import javax.swing.SwingUtilities

class StateWidgetPillBarAction : SegmentedBarActionComponent(ActionPlaces.STATE_WIDGET_ACTION_BAR) {
  init {
    ActionManager.getInstance().getAction("StateWidgetBarGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
  }
}