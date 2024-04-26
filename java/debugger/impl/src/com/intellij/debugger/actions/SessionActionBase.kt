// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

abstract class SessionActionBase: DebuggerAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = debuggerSession(e) != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  protected fun debuggerSession(e: AnActionEvent) =
    e.project?.let { DebuggerManagerEx.getInstanceEx(it).context.debuggerSession }
}