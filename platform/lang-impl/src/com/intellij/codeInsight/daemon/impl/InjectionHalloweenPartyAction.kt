// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * Turns [InjectionHalloweenParty] on and off.
 * The party is off by default, and the registry keeps the state after a restart of the IDE.
 */
internal class InjectionHalloweenPartyAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return isInjectionHalloweenPartyOn()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    setInjectionHalloweenPartyOn(state)
  }
}
