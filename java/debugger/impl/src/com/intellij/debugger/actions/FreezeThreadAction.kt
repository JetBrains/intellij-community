// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

class FreezeThreadAction : DebuggerAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val selectedNode = getSelectedNodes(e.dataContext)
    if (selectedNode == null) {
      return
    }
    val debuggerContext = getDebuggerContext(e.dataContext)
    val debugProcess = debuggerContext.debugProcess
    if (debugProcess == null) return

    for (debuggerTreeNode in selectedNode) {
      val threadDescriptor = (debuggerTreeNode.descriptor as ThreadDescriptorImpl)
      if (!threadDescriptor.isFrozen) {
        val managerThread = debuggerContext.getManagerThread() ?: return
        freezeThread(threadDescriptor.threadReference, debugProcess, managerThread)
        ApplicationManager.getApplication().invokeLater(Runnable { debuggerTreeNode.calcValue() })
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val selectedNode = getSelectedNodes(e.dataContext)
    if (selectedNode == null) {
      return
    }
    val debugProcess = getDebuggerContext(e.dataContext).debugProcess

    var visible = false
    if (debugProcess != null) {
      visible = true
      for (aSelectedNode in selectedNode) {
        val threadDescriptor = aSelectedNode.descriptor
        if (threadDescriptor !is ThreadDescriptorImpl || threadDescriptor.isSuspended) {
          visible = false
          break
        }
      }
    }
    e.presentation.text = JavaDebuggerBundle.message("action.freeze.thread.text")
    e.presentation.setVisible(visible)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {
    fun freezeThread(thread: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
      executeOnDMT(managerThread) {
        managerThread.invokeNow(debugProcess.createFreezeThreadCommand(thread))
      }
    }
  }
}
