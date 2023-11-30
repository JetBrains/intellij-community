// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.action

import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus

/**
 * Action group will be disabled if all children are disabled
 */
@ApiStatus.Internal
class AutoDisablingActionGroup(shortName: @NlsActions.ActionText String?, popup: Boolean) : DefaultActionGroup(shortName, popup) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val enabledActions = ActionGroupUtil.getActiveActions(this, e)
    e.presentation.isEnabled = enabledActions.isNotEmpty
  }
}