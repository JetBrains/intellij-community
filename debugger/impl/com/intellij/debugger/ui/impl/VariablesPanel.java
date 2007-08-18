package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class VariablesPanel extends DebuggerTreePanel implements DataProvider{

  @NonNls private static final String HELP_ID = "debugging.debugFrame";

  public VariablesPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    setBorder(null);


    final FrameDebuggerTree frameTree = getFrameTree();

    add(new JScrollPane(frameTree), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE));

    overrideShortcut(frameTree, DebuggerActions.SET_VALUE, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
  }


  protected DebuggerTree createTreeView() {
    return new FrameDebuggerTree(getProject());
  }


  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.FRAME_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.FRAME_PANEL_POPUP, group);
  }

  public Object getData(String dataId) {
    if (DataConstants.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }


  public FrameDebuggerTree getFrameTree() {
    return (FrameDebuggerTree) getTree();
  }

}