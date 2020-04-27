// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;

public class RemoveCustomFieldAction extends EditCustomFieldAction {
  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    UserExpressionDescriptorImpl descriptor = (UserExpressionDescriptorImpl)((JavaValue)node.getValueContainer()).getDescriptor();
    EnumerationChildrenRenderer enumerationChildrenRenderer = getParentEnumerationRenderer(descriptor);
    if (enumerationChildrenRenderer != null) {
      enumerationChildrenRenderer.getChildren().remove(descriptor.getEnumerationIndex());
      TreeNode parent = node.getParent();
      int index = parent.getIndex(node);
      int indexToSelect = index + 1 < parent.getChildCount() ? index + 1 : index - 1;
      TreeUtil.selectNode(node.getTree(), indexToSelect >= 0 ? parent.getChildAt(indexToSelect) : parent);

      XDebuggerUtilImpl.rebuildTreeAndViews(node.getTree());
    }
  }
}
