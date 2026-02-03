// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger

import com.intellij.debugger.actions.DebuggerAction
import com.intellij.debugger.actions.findJavaValue
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object JvmDebuggerUtils {
  @JvmStatic
  fun findProxyFromContext(debuggerContext: DebuggerContextImpl): XDebugSessionProxy? {
    val session = debuggerContext.debuggerSession?.xDebugSession ?: return null
    return XDebuggerEntityConverter.asProxy(session)
  }

  @JvmStatic
  fun getDescriptorFromNode(node: XValueContainerNode<*>, debuggerContext: DebuggerContextImpl): ValueDescriptorImpl? {
    val sessionProxy = findProxyFromContext(debuggerContext)
    val javaValue = if (sessionProxy != null) {
      val xValue: XValue = node.getValueContainer() as? XValue ?: return null
      findJavaValue(xValue, sessionProxy)
                  // Most likely it is a full remote mode, and it is not implemented yet
                  ?: return null
    }
    else {
      node.getValueContainer() as JavaValue
    }
    return javaValue.descriptor
  }

  @JvmStatic
  fun getDescriptorFromNode(node: XValueContainerNode<*>, e: AnActionEvent): ValueDescriptorImpl? =
    getDescriptorFromNode(node, DebuggerAction.getDebuggerContext(e.dataContext))
}
