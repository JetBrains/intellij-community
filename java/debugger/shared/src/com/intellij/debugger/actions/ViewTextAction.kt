// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.java.debugger.impl.shared.actions.ViewTextActionBase
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus

class ViewTextAction : ViewTextActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @ApiStatus.Internal
  override fun getStringNode(e: AnActionEvent): XValueNodeImpl? {
    val node = XDebuggerTreeActionBase.getSelectedNodes(e.dataContext).singleOrNull() ?: return null
    val xValue = node.valueContainer
    val descriptor = xValue.xValueDescriptorAsync?.getNow(null) as? JavaValueDescriptor ?: return null

    if (xValue.modifier != null && descriptor.isString) {
      return node
    }

    return null
  }
}