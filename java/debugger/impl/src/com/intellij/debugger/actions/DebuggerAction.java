// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class DebuggerAction
 * @author Jeka
 */
package com.intellij.debugger.actions;


import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.DebuggerTreePanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DoubleClickListener;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class DebuggerAction extends AnAction {
  private static class Holder {
    private static final DebuggerTreeNodeImpl[] EMPTY_TREE_NODE_ARRAY = new DebuggerTreeNodeImpl[0];
  }

  public static @Nullable DebuggerTree getTree(DataContext dataContext) {
    return DebuggerTree.DATA_KEY.getData(dataContext);
  }

  public static @Nullable DebuggerTreePanel getPanel(DataContext dataContext) {
    return DebuggerTreePanel.DATA_KEY.getData(dataContext);
  }

  public static @Nullable DebuggerTreeNodeImpl getSelectedNode(DataContext dataContext) {
    DebuggerTree tree = getTree(dataContext);
    if (tree == null) return null;

    if (tree.getSelectionCount() != 1) {
      return null;
    }
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    Object component = path.getLastPathComponent();
    if (!(component instanceof DebuggerTreeNodeImpl)) {
      return null;
    }
    return (DebuggerTreeNodeImpl)component;
  }

  public static DebuggerTreeNodeImpl @Nullable [] getSelectedNodes(DataContext dataContext) {
    DebuggerTree tree = getTree(dataContext);
    if (tree == null) return null;
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return Holder.EMPTY_TREE_NODE_ARRAY;
    }
    List<DebuggerTreeNodeImpl> nodes = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      Object component = path.getLastPathComponent();
      if (component instanceof DebuggerTreeNodeImpl) {
        nodes.add((DebuggerTreeNodeImpl)component);
      }
    }
    return nodes.toArray(new DebuggerTreeNodeImpl[0]);
  }

  public static @NotNull DebuggerContextImpl getDebuggerContext(DataContext dataContext) {
    DebuggerTreePanel panel = getPanel(dataContext);
    if (panel != null) {
      return panel.getContext();
    }
    else {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      return project != null ? (DebuggerManagerEx.getInstanceEx(project)).getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
    }
  }

  public static Disposable installEditAction(final JTree tree, String actionName) {
    final DoubleClickListener listener = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (tree.getPathForLocation(e.getX(), e.getY()) == null) return false;
        DataContext dataContext = DataManager.getInstance().getDataContext(tree);
        GotoFrameSourceAction.doAction(dataContext);
        return true;
      }
    };
    listener.installOn(tree);

    Disposable disposable = () -> listener.uninstall(tree);
    DebuggerUIUtil.registerActionOnComponent(actionName, tree, disposable);

    return disposable;
  }

  public static void refreshViews(final AnActionEvent e) {
    refreshViews(DebuggerUIUtil.getSession(e));
  }

  public static void refreshViews(@Nullable XDebugSession session) {
    if (session != null) {
      XDebugProcess process = session.getDebugProcess();
      if (process instanceof JavaDebugProcess) {
        ((JavaDebugProcess)process).saveNodeHistory();
      }
      session.rebuildViews();
    }
  }

  public static boolean isInJavaSession(AnActionEvent e) {
    XDebugSession session = DebuggerUIUtil.getSession(e);
    return session != null && session.getDebugProcess() instanceof JavaDebugProcess;
  }

  static JavaStackFrame getStackFrame(AnActionEvent e) {
    StackFrameDescriptorImpl descriptor = getSelectedStackFrameDescriptor(e);
    if (descriptor != null) {
      return new JavaStackFrame(descriptor, false);
    }
    return getSelectedStackFrame(e);
  }

  static StackFrameProxyImpl getStackFrameProxy(AnActionEvent e) {
    DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      NodeDescriptorImpl descriptor = node.getDescriptor();
      if (descriptor instanceof StackFrameDescriptorImpl) {
        return ((StackFrameDescriptorImpl)descriptor).getFrameProxy();
      }
    }
    else {
      JavaStackFrame stackFrame = getSelectedStackFrame(e);
      if (stackFrame != null) {
        return stackFrame.getStackFrameProxy();
      }
    }
    return null;
  }

  private static @Nullable StackFrameDescriptorImpl getSelectedStackFrameDescriptor(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if (descriptor instanceof StackFrameDescriptorImpl) {
        return (StackFrameDescriptorImpl)descriptor;
      }
    }
    return null;
  }

  private static @Nullable JavaStackFrame getSelectedStackFrame(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = DebuggerUIUtil.getSession(e);
      if (session != null) {
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          return ((JavaStackFrame)frame);
        }
      }
    }
    return null;
  }
}
