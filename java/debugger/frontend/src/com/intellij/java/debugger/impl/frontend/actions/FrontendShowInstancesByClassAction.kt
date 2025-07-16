// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.frontend.actions

import com.intellij.java.debugger.impl.frontend.messages.JavaDebuggerImplFrontendBundle
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frontend version of [com.intellij.debugger.memory.action.ShowInstancesByClassAction]
 */
private class FrontendShowInstancesByClassAction : XDebuggerTreeActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    if (DebuggerUIUtil.getSessionProxy(e) == null) return false
    val xValue = node.valueContainer
    val descriptor = xValue.xValueDescriptorAsync?.getNow(null) as? JavaValueDescriptor ?: return false
    val objectReferenceInfo = descriptor.objectReferenceInfo ?: return false

    val visibleTypeName = StringUtil.getShortName(objectReferenceInfo.typeName)
    e.presentation.setText(JavaDebuggerImplFrontendBundle.message("action.show.objects.text", visibleTypeName))

    return objectReferenceInfo.canGetInstanceInfo
  }


  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val xValue = node.valueContainer
    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    service<FrontendShowInstancesByClassActionCoroutineScope>().cs.launch {
      XDebugManagerProxy.getInstance().withId(xValue, session) { xValueId ->
        JavaDebuggerLuxActionsApi.getInstance().showInstancesDialog(xValueId)
      }
    }
  }
}

@Service(Service.Level.APP)
private class FrontendShowInstancesByClassActionCoroutineScope(val cs: CoroutineScope)