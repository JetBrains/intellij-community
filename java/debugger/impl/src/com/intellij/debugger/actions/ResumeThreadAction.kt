// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ResumeThreadAction : DebuggerAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val selectedNodes = getSelectedNodes(e.dataContext) ?: return
    val debuggerContext = getDebuggerContext(e.dataContext)
    val debugProcess = debuggerContext.debugProcess

    if (debugProcess == null) return

    for (debuggerTreeNode in selectedNodes) {
      val threadDescriptor = (debuggerTreeNode.descriptor as ThreadDescriptorImpl)
      val managerThread = debuggerContext.getManagerThread() ?: return
      if (threadDescriptor.isSuspended) {
        resumeThread(threadDescriptor.threadReference, debugProcess, managerThread)
        ApplicationManager.getApplication().invokeLater(Runnable { debuggerTreeNode.calcValue() })
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val selectedNodes = getSelectedNodes(e.dataContext)

    var visible = false

    if (selectedNodes != null && selectedNodes.size > 0) {
      visible = true
      for (selectedNode in selectedNodes) {
        val threadDescriptor = selectedNode.descriptor
        if (threadDescriptor !is ThreadDescriptorImpl || !threadDescriptor.isSuspended) {
          visible = false
          break
        }
      }
    }
    e.presentation.apply {
      isEnabledAndVisible = visible
      text = JavaDebuggerBundle.message("action.resume.thread.text")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {
    fun resumeThread(thread: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
      executeOnDMT(managerThread) {
        val suspendingContext = SuspendManagerUtil.getSuspendingContext(debugProcess.suspendManager, thread)
        if (suspendingContext != null) {
          managerThread.invokeNow(debugProcess.createResumeThreadCommand(suspendingContext, thread))
        }
      }
    }
  }
}