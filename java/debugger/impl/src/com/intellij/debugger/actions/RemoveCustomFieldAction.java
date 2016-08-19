/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author egor
 */
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
