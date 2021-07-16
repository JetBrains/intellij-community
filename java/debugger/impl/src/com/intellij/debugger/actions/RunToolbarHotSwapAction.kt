// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.HotSwapUI
import com.intellij.debugger.ui.HotSwapUIImpl
import com.intellij.execution.runToolbar.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import java.util.*

class RunToolbarHotSwapAction : AnAction(), RTBarAction {
  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val session = getSession(e)
    if (session != null && session.isAttached) {
      HotSwapUI.getInstance(project).reloadChangedClasses(session, DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP)
    }
  }

  private fun getSession(e: AnActionEvent): DebuggerSession? {
    return e.environment()?.let { environment ->
      e.project?.let { project ->
        val xDebugSession = XDebuggerManager.getInstance(project)
          ?.debugSessions
          ?.filter { it.runContentDescriptor == environment.contentToReuse }
          ?.filterIsInstance<XDebugSessionImpl>()?.firstOrNull { !it.isStopped }

        DebuggerManagerEx.getInstanceEx(project).sessions.firstOrNull{session -> Objects.equals(session.getXDebugSession(), xDebugSession)}
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val session = getSession(e)
    e.presentation.isEnabledAndVisible =
      (if(e.isItRunToolbarMainSlot()) RunToolbarSlotManager.getInstance(project).getState().isSingleProcess() || e.isOpened() else true)
      && session != null
      && HotSwapUIImpl.canHotSwap(session)
      && Registry.`is`("ide.new.navbar.hotswap", false)

  }
}