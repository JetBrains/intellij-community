// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class DebuggerAction
 * @author Jeka
 */
package com.intellij.debugger.actions;


import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.DebuggerTreePanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
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
import com.intellij.xdebugger.XDebuggerManager;
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

  @Nullable
  public static DebuggerTree getTree(DataContext dataContext){
    return DebuggerTree.DATA_KEY.getData(dataContext);
  }

  @Nullable
  public static DebuggerTreePanel getPanel(DataContext dataContext){
    return DebuggerTreePanel.DATA_KEY.getData(dataContext);
  }

  @Nullable
  public static DebuggerTreeNodeImpl getSelectedNode(DataContext dataContext) {
    DebuggerTree tree = getTree(dataContext);
    if(tree == null) return null;

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
    if(tree == null) return null;
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return Holder.EMPTY_TREE_NODE_ARRAY;
    }
    List<DebuggerTreeNodeImpl> nodes = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      Object component = path.getLastPathComponent();
      if (component instanceof DebuggerTreeNodeImpl) {
        nodes.add((DebuggerTreeNodeImpl) component);
      }
    }
    return nodes.toArray(new DebuggerTreeNodeImpl[0]);
  }

  @NotNull
  public static DebuggerContextImpl getDebuggerContext(DataContext dataContext) {
    DebuggerTreePanel panel = getPanel(dataContext);
    if(panel != null) {
      return panel.getContext();
    } else {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      return project != null ? (DebuggerManagerEx.getInstanceEx(project)).getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
    }
  }

  public static boolean isContextView(AnActionEvent e) {
    return DebuggerActions.EVALUATION_DIALOG_POPUP.equals(e.getPlace()) ||
           DebuggerActions.FRAME_PANEL_POPUP.equals(e.getPlace()) ||
           DebuggerActions.WATCH_PANEL_POPUP.equals(e.getPlace()) ||
           DebuggerActions.INSPECT_PANEL_POPUP.equals(e.getPlace());
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

  public static boolean isFirstStart(final AnActionEvent event) {
    //noinspection HardCodedStringLiteral
    String key = "initalized";
    if(event.getPresentation().getClientProperty(key) != null) return false;

    event.getPresentation().putClientProperty(key, key);
    return true;
  }

  public static void enableAction(final AnActionEvent event, final boolean enable) {
    SwingUtilities.invokeLater(() -> {
      event.getPresentation().setEnabled(enable);
      event.getPresentation().setVisible(true);
    });
  }

  public static void refreshViews(final AnActionEvent e) {
    refreshViews(getSession(e));
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
    XDebugSession session = getSession(e);
    return session != null && session.getDebugProcess() instanceof JavaDebugProcess;
  }

  @Nullable
  public static XDebugSession getSession(AnActionEvent e) {
    XDebugSession session = e.getData(XDebugSession.DATA_KEY);
    if (session == null) {
      Project project = e.getProject();
      if (project != null) {
        session = XDebuggerManager.getInstance(project).getCurrentSession();
      }
    }
    return session;
  }
}
