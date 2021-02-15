// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.PillActionComponent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry
import javax.swing.SwingUtilities

class StateWidgetAction : PillActionComponent(), DumbAware {
  companion object {
    const val runDebugKey = "ide.new.navbar"
  }

  private fun isNewRunDebug(): Boolean {
    return Registry.get(runDebugKey).asBoolean()
  }

  init {
    ActionManager.getInstance().getAction("StateWidgetPillActionGroup")?.let {
      if(it is ActionGroup) {
        SwingUtilities.invokeLater {
          actionGroup = it
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isNewRunDebug()

    e.presentation.putClientProperty(PILL_SHOWN, e.project?.let {
      StateWidgetManager.getInstance(it).getExecutionsCount() > 0
    } ?: false)
  }
}