// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.sun.jdi.request.EventRequest

class RepairDebuggerViewAction : SessionActionBase(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val session = debuggerSession(e) ?: return
    val process = session.process
    process.managerThread.schedule(object : DebuggerCommandImpl() {
      override fun action() {
        val suspendManager = process.suspendManager
        val eventContexts = suspendManager.eventContexts
        val suspendAllContexts = eventContexts.filter { it.suspendPolicy == EventRequest.SUSPEND_ALL }

        thisLogger().warn("All paused contexts: $eventContexts")

        val suspendContextToRestore = if (suspendAllContexts.size > 1) {
          thisLogger().error("A lot of suspend all contexts: $suspendAllContexts")
          val contextsWithEventSet = suspendAllContexts.filter { it.eventSet != null }
          if (contextsWithEventSet.size > 1) {
            thisLogger().error("A lot of suspend all contexts with event set: $contextsWithEventSet")
          }
          if (contextsWithEventSet.isNotEmpty()) {
            contextsWithEventSet[0]
          }
          else suspendAllContexts[0]
        }
        else if (suspendAllContexts.isNotEmpty()) {
          suspendAllContexts[0]
        }
        else if (eventContexts.isNotEmpty()) {
          thisLogger().warn("Only thread-paused contexts available")
          eventContexts[0]
        }
        else {
          thisLogger().error("Cannot find a context to restore debugger view")
          return
        }

        val restoredDebuggerContext = DebuggerContextImpl.createDebuggerContext(session, suspendContextToRestore, suspendContextToRestore.eventThread, null)
        DebuggerInvocationUtil.invokeLater(process.project) {
          session.contextManager.setState(restoredDebuggerContext, DebuggerSession.State.PAUSED, DebuggerSession.Event.PAUSE, null)
        }
      }
    })
  }
}
