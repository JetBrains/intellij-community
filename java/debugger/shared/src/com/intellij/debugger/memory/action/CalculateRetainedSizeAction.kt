// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.launch

class CalculateRetainedSizeAction : XDebuggerTreeActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return
    val xValue = node.valueContainer
    sessionProxy.coroutineScope.launch {
      XDebugManagerProxy.getInstance().withId(xValue, sessionProxy) { xValueId ->
        JavaDebuggerLuxActionsApi.getInstance().showCalculateRetainedSizeDialog(xValueId, nodeName)
      }
    }
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    if (!super.isEnabled(node, e)) return false

    val javaSession = SharedJavaDebuggerSession.findSession(e)

    if (javaSession == null || !javaSession.isEvaluationPossible || !DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) {
      e.presentation.setVisible(false)
      return false
    }

    val xValue = node.valueContainer
    val descriptor = xValue.xValueDescriptorAsync?.getNow(null) as? JavaValueDescriptor ?: return false
    val objectReferenceInfo = descriptor.objectReferenceInfo
    return objectReferenceInfo != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
