/*
 * Class DebuggerAction
 * @author Jeka
 */
package com.intellij.debugger.actions;


import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.DebuggerPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class DebuggerAction extends AnAction {
  private static DebuggerTreeNodeImpl[] EMPTY_TREE_NODE_ARRAY = new DebuggerTreeNodeImpl[0];

  @Nullable
  public static DebuggerTree getTree(DataContext dataContext){
    return (DebuggerTree)dataContext.getData(DebuggerActions.DEBUGGER_TREE);
  }

  @Nullable
  public static DebuggerPanel getPanel(DataContext dataContext){
    return (DebuggerPanel)dataContext.getData(DebuggerActions.DEBUGGER_PANEL);
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
    List<Object> nodes = new ArrayList<Object>(paths.length);
    for (TreePath path : paths) {
      Object component = path.getLastPathComponent();
      if (component instanceof DebuggerTreeNodeImpl) {
        nodes.add(component);
      }
    }
    return nodes.toArray(new DebuggerTreeNodeImpl[nodes.size()]);
  }

  public static DebuggerContextImpl getDebuggerContext(DataContext dataContext) {
    DebuggerPanel panel = getPanel(dataContext);
    if(panel != null) {
      return panel.getContext();
    } else {
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      return project != null ? (DebuggerManagerEx.getInstanceEx(project)).getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
    }
  }

  @Nullable
  public static DebuggerStateManager getContextManager(DataContext dataContext) {
    DebuggerPanel panel = getPanel(dataContext);
    return panel == null ? null : panel.getContextManager();
  }

  public static boolean isContextView(AnActionEvent e) {
    return DebuggerActions.EVALUATION_DIALOG_POPUP.equals(e.getPlace()) ||
           DebuggerActions.FRAME_PANEL_POPUP.equals(e.getPlace()) ||
           DebuggerActions.WATCH_PANEL_POPUP.equals(e.getPlace()) ||
           DebuggerActions.INSPECT_PANEL_POPUP.equals(e.getPlace());
  }

  public static Disposable installEditAction(final JTree tree, String actionName) {
    final MouseAdapter mouseListener = new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (tree.getPathForLocation(e.getX(), e.getY()) == null) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(tree);
        GotoFrameSourceAction.doAction(dataContext);
      }
    };
    tree.addMouseListener(mouseListener);
    
    final AnAction action = ActionManager.getInstance().getAction(actionName);
    action.registerCustomShortcutSet(CommonShortcuts.getEditSource(), tree);

    return new Disposable() {
      public void dispose() {
        tree.removeMouseListener(mouseListener);
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
}