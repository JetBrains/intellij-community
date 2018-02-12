/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.render.ArrayFilterInplaceEditor;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * @author egor
 */
public abstract class ArrayFilterAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getFilterNode(e) != null);
  }

  public static boolean isArrayFilter(TreeNode node) {
    return node instanceof MessageTreeNode && ((MessageTreeNode)node).getLink() == ArrayRenderer.Filtered.FILTER_HYPERLINK;
  }

  @Nullable
  private static MessageTreeNode getFilterNode(AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e.getDataContext());
    if (tree != null) {
      TreePath[] paths = tree.getSelectionPaths();
      if (!ArrayUtil.isEmpty(paths) && paths.length == 1) {
        Object node = paths[0].getLastPathComponent();
        if (isArrayFilter((TreeNode)node)) {
          return (MessageTreeNode)node;
        }
      }
    }
    return null;
  }

  public static class Edit extends ArrayFilterAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      MessageTreeNode node = getFilterNode(e);
      if (node != null) {
        ArrayFilterInplaceEditor.edit(node, false);
      }
    }
  }

  public static class Delete extends ArrayFilterAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      MessageTreeNode node = getFilterNode(e);
      if (node != null) {
        TreeUtil.selectNode(node.getTree(), node.getParent());
        ArrayAction.setArrayRenderer(NodeRendererSettings.getInstance().getArrayRenderer(),
                                     (XValueNodeImpl)node.getParent(),
                                     DebuggerManagerEx.getInstanceEx(e.getProject()).getContext());
      }
    }
  }
}
