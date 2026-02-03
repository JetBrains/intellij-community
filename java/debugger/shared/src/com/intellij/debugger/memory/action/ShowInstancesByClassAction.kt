// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action

import com.intellij.java.debugger.impl.shared.JavaDebuggerSharedBundle
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeSplitActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.launch

class ShowInstancesByClassAction : XDebuggerTreeSplitActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    if (DebuggerUIUtil.getSessionProxy(e) == null) return false
    val xValue = node.valueContainer
    if (!XDebugManagerProxy.getInstance().hasBackendCounterpart(xValue)) return false
    val descriptor = xValue.xValueDescriptorAsync?.getNow(null) as? JavaValueDescriptor ?: return false
    val objectReferenceInfo = descriptor.objectReferenceInfo ?: return false

    val visibleTypeName = StringUtil.getShortName(objectReferenceInfo.typeName)
    e.presentation.setText(JavaDebuggerSharedBundle.message("action.show.objects.text", visibleTypeName))

    return objectReferenceInfo.canGetInstanceInfo
  }

  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val xValue = node.valueContainer
    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    session.coroutineScope.launch {
      XDebugManagerProxy.getInstance().withId(xValue, session) { xValueId ->
        JavaDebuggerLuxActionsApi.getInstance().showInstancesDialog(xValueId)
      }
    }
  }
}