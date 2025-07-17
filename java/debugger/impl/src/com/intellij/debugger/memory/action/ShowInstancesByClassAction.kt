// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.memory.filtering.ClassInstancesProvider
import com.intellij.debugger.memory.ui.InstancesWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

/**
 * See the frontend version of the action: com.intellij.java.debugger.impl.frontend.actions.FrontendShowInstancesByClassAction
 */
class ShowInstancesByClassAction : DebuggerTreeAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    val ref = getObjectReference(node)
    val enabled = ref != null && ref.virtualMachine().canGetInstanceInfo()
    if (enabled) {
      val text = JavaDebuggerBundle.message("action.show.objects.text", StringUtil.getShortName(ref.referenceType().name()))
      e.presentation.setText(text)
    }

    return enabled
  }

  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      val debugSession = DebuggerUIUtil.getSession(e)
      val ref = getObjectReference(node)
      if (debugSession != null && ref != null) {
        val referenceType = ref.referenceType()
        InstancesWindow(debugSession, ClassInstancesProvider(referenceType), referenceType).show()
      }
    }
  }
}
