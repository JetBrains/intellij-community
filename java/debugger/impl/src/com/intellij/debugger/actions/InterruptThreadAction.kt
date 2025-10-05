// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InterruptThreadAction : DebuggerAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val nodes = getSelectedNodes(e.dataContext)
    if (nodes == null) {
      return
    }

    val threadsToInterrupt = nodes.mapNotNull {
      val descriptor = it.descriptor
      if (descriptor is ThreadDescriptorImpl) descriptor.threadReference else null
    }

    if (threadsToInterrupt.isEmpty()) return
    val debuggerContext = getDebuggerContext(e.dataContext)
    val debugProcess = debuggerContext.debugProcess ?: return
    val managerThread = debuggerContext.getManagerThread() ?: return

    for (thread in threadsToInterrupt) {
      interruptThread(thread, debugProcess, managerThread)
    }
  }

  override fun update(e: AnActionEvent) {
    val selectedNodes = getSelectedNodes(e.dataContext)

    var visible = false
    var enabled = false

    if (selectedNodes != null && selectedNodes.size > 0) {
      visible = true
      enabled = true
      for (selectedNode in selectedNodes) {
        val threadDescriptor = selectedNode.descriptor
        if (threadDescriptor !is ThreadDescriptorImpl) {
          visible = false
          break
        }
      }

      if (visible) {
        for (selectedNode in selectedNodes) {
          val threadDescriptor = selectedNode.descriptor as ThreadDescriptorImpl
          if (threadDescriptor.isFrozen || threadDescriptor.isSuspended) {
            enabled = false
            break
          }
        }
      }
    }
    val presentation = e.presentation
    presentation.setText(JavaDebuggerBundle.messagePointer("action.interrupt.thread.text"))
    presentation.setEnabledAndVisible(visible && enabled)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {
    fun interruptThread(thread: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
      executeOnDMT(managerThread) {
        try {
          thread.getThreadReference().interrupt()
        }
        catch (_: UnsupportedOperationException) {
          val project = debugProcess.project
          XDebuggerManagerImpl.getNotificationGroup()
            .createNotification(JavaDebuggerBundle.message("thread.operation.interrupt.is.not.supported.by.vm"),
                                MessageType.INFO).notify(project)
        }
      }
    }
  }
}
