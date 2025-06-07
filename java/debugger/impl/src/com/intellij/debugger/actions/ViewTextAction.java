// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.java.debugger.impl.shared.actions.ViewTextActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/*
 * @author Jeka
 * @see com.intellij.java.debugger.impl.frontend.actions.FrontendViewTextAction
 */
public class ViewTextAction extends ViewTextActionBase {
  @ApiStatus.Internal
  @Override
  protected XValueNodeImpl getStringNode(@NotNull AnActionEvent e) {
    List<XValueNodeImpl> selectedNodes = XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());
    if (selectedNodes.size() == 1) {
      XValueNodeImpl node = selectedNodes.get(0);
      XValue container = node.getValueContainer();
      if (container instanceof JavaValue && container.getModifier() != null && ((JavaValue)container).getDescriptor().isString()) {
        return node;
      }
    }
    return null;
  }
}
