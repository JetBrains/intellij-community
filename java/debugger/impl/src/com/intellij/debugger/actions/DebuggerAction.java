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

/*
 * Class DebuggerAction
 * @author Jeka
 */
package com.intellij.debugger.actions;


import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.DebuggerTreePanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DoubleClickListener;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class DebuggerAction extends AnAction {
  private static final DebuggerTreeNodeImpl[] EMPTY_TREE_NODE_ARRAY = new DebuggerTreeNodeImpl[0];

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

  @Nullable
  public static DebuggerTreeNodeImpl[] getSelectedNodes(DataContext dataContext) {
    DebuggerTree tree = getTree(dataContext);
    if(tree == null) return null;
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return EMPTY_TREE_NODE_ARRAY;
    }
    List<DebuggerTreeNodeImpl> nodes = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      Object component = path.getLastPathComponent();
      if (component instanceof DebuggerTreeNodeImpl) {
        nodes.add((DebuggerTreeNodeImpl) component);
      }
    }
    return nodes.toArray(new DebuggerTreeNodeImpl[nodes.size()]);
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

  @Nullable
  public static DebuggerStateManager getContextManager(DataContext dataContext) {
    DebuggerTreePanel panel = getPanel(dataContext);
    return panel == null ? null : panel.getContextManager();
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
      protected boolean onDoubleClick(MouseEvent e) {
        if (tree.getPathForLocation(e.getX(), e.getY()) == null) return false;
        DataContext dataContext = DataManager.getInstance().getDataContext(tree);
        GotoFrameSourceAction.doAction(dataContext);
        return true;
      }
    };
    listener.installOn(tree);

    final AnAction action = ActionManager.getInstance().getAction(actionName);
    action.registerCustomShortcutSet(CommonShortcuts.getEditSource(), tree);

    return new Disposable() {
      public void dispose() {
        listener.uninstall(tree);
        action.unregisterCustomShortcutSet(tree);
      }
    };
  }

  public static boolean isFirstStart(final AnActionEvent event) {
    //noinspection HardCodedStringLiteral
    String key = "initalized";
    if(event.getPresentation().getClientProperty(key) != null) return false;

    event.getPresentation().putClientProperty(key, key);
    return true;
  }

  public static void enableAction(final AnActionEvent event, final boolean enable) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        event.getPresentation().setEnabled(enable);
        event.getPresentation().setVisible(true);
      }
    });
  }

  public static void refreshViews(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e.getDataContext());
    if (tree != null) {
    refreshViews(XDebugView.getSession(tree));
    }
  }

  public static void refreshViews(@NotNull XValueNodeImpl node) {
    refreshViews(XDebugView.getSession(node.getTree()));
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
}
