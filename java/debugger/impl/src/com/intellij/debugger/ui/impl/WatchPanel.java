/*
 * Class WatchPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;

public abstract class WatchPanel extends DebuggerTreePanel {
  @NonNls private static final String HELP_ID = "debugging.debugWatches";

  public WatchPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    add(new JScrollPane(getWatchTree()), BorderLayout.CENTER);
    final Disposable disposable = DebuggerAction.installEditAction(getWatchTree(), DebuggerActions.EDIT_NODE_SOURCE);
    registerDisposable(disposable);
  }

  protected DebuggerTree createTreeView() {
    return new WatchDebuggerTree(getProject());
  }

  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if(event == DebuggerSession.EVENT_ATTACHED) {
      DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getWatchTree().getModel().getRoot();
      if(root != null) {
        for(Enumeration e = root.rawChildren(); e.hasMoreElements();) {
          DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl) e.nextElement();
          ((WatchItemDescriptor) child.getDescriptor()).setNew();
        }
      }
    }

    rebuildIfVisible(event);
  }

  protected ActionPopupMenu createPopupMenu() {
    return null;
  }

  public Object getData(String dataId) {
    if (DataConstants.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  public WatchDebuggerTree getWatchTree() {
    return (WatchDebuggerTree) getTree();
  }
}