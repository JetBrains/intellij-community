// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions

import com.intellij.application.options.RegistryManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.HotSwapUI
import com.intellij.debugger.ui.HotSwapUIImpl
import com.intellij.execution.runToolbar.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import java.util.*

class RunToolbarHotSwapAction : AnAction(), RTBarAction {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarHotSwapAction::class.java)
  }

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val session = getSession(e)
    if (session != null && session.isAttached) {
      HotSwapUI.getInstance(project).reloadChangedClasses(session, DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP)
    }
  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}

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
    val session = getSession(e)
    e.presentation.isVisible =
      session != null
      && HotSwapUIImpl.canHotSwap(session)
      && RegistryManager.getInstance().`is`("ide.widget.toolbar.hotswap")

    if(e.presentation.isVisible) {
      e.presentation.isEnabled = !e.isProcessTerminating()
    }

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }

    //LOG.info(getLog(e))
  }
}