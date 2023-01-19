// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UIDensity
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

/**
 * @author Konstantin Bulenkov
 */
abstract class SetDensityAction(val density: UIDensity): DumbAwareToggleAction() {

  override fun isSelected(e: AnActionEvent) = LafManager.getInstance().density == density

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state && !isSelected(e)) {
      LafManager.getInstance().density = density
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

class SetDefaultDensityAction: SetDensityAction(UIDensity.DEFAULT)
class SetCompactDensityAction: SetDensityAction(UIDensity.COMPACT)