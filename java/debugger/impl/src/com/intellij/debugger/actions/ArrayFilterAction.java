// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.render.ArrayFilterInplaceEditor;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public abstract class ArrayFilterAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getFilterNode(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public static boolean isArrayFilter(TreeNode node) {
    return node instanceof MessageTreeNode && ((MessageTreeNode)node).getLink() == ArrayRenderer.Filtered.FILTER_HYPERLINK;
  }

  private static @Nullable MessageTreeNode getFilterNode(AnActionEvent e) {
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
    public void actionPerformed(@NotNull AnActionEvent e) {
      MessageTreeNode node = getFilterNode(e);
      XDebugSessionProxy sessionProxy = DebuggerUIUtil.getSessionProxy(e);
      if (node != null && sessionProxy != null) {
        ArrayFilterInplaceEditor.edit(node, false, sessionProxy);
      }
    }
  }

  public static class Delete extends ArrayFilterAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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
