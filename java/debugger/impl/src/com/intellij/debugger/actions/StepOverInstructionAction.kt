// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware

class StepOverInstructionAction : DebuggerAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    debuggerSession(e)?.stepOverInstruction() ?: thisLogger().error("Inconsistent update")
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = debuggerSession(e) != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private fun debuggerSession(e: AnActionEvent) =
    e.project?.let { DebuggerManagerEx.getInstanceEx(it).context.debuggerSession }
}

