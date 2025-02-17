// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.frontend.actions

import com.intellij.java.debugger.impl.shared.actions.ViewTextActionBase
import com.intellij.java.debugger.impl.shared.engine.JAVA_VALUE_KIND
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.frame.XValueType
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

/**
 * Frontend version of [ViewTextAction]
 */
private class FrontendViewTextAction : ViewTextActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun getStringNode(e: AnActionEvent): XValueNodeImpl? {
    val node = XDebuggerTreeActionBase.getSelectedNodes(e.dataContext).singleOrNull() ?: return null
    val xValue = node.valueContainer as? FrontendXValue ?: return null
    val descriptor = xValue.descriptor ?: return null

    if (xValue.modifier != null && descriptor.kind == JAVA_VALUE_KIND && descriptor.type is XValueType.StringType) {
      return node
    }

    return null
  }
}