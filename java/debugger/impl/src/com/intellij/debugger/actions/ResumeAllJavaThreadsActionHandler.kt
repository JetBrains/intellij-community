// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

class ResumeAllJavaThreadsActionHandler(val debugProcess: DebugProcessImpl) : DebuggerActionHandler() {
  override fun perform(project: Project, event: AnActionEvent) {
    val xdebugProcess = debugProcess.xdebugProcess ?: return

    debugProcess.managerThread.schedule(object : DebuggerCommandImpl() {
      override fun action() {
        val contexts: MutableList<SuspendContextImpl> = debugProcess.suspendManager.pausedContexts
        for (context in contexts) {
          if (!context.isResumed) {
            debugProcess.suspendManager.resume(context)
          }
        }
        DebuggerUIUtil.invokeLater(Runnable {
          xdebugProcess.session.resume()
        })
      }
    })
  }

  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    return !debugProcess.suspendManager.pausedContexts.isEmpty()
  }
}