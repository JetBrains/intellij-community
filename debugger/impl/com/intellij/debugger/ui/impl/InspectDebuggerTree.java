package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.ui.PopupHandler;

import java.awt.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class InspectDebuggerTree extends DebuggerTree{
  private NodeDescriptorImpl myInspectDescriptor;

  public InspectDebuggerTree(Project project) {
    super(project);

    final PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = createPopupMenu();
        if (popupMenu != null) {
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    };
    addMouseListener(popupHandler);

    new ValueNodeDnD(this, project);
  }

  public static ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.INSPECT_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.INSPECT_PANEL_POPUP, group);
  }

  protected void build(DebuggerContextImpl context) {
    updateNode(context);
  }

  public void setInspectDescriptor(NodeDescriptorImpl inspectDescriptor) {
    myInspectDescriptor = inspectDescriptor;
  }

  public NodeDescriptorImpl getInspectDescriptor() {
    return myInspectDescriptor;
  }



  private void updateNode(final DebuggerContextImpl context) {
    context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
      public void threadAction() {
        final DebuggerTreeNodeImpl node = getNodeFactory().createNode(myInspectDescriptor, context.createEvaluationContext());

        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
            root.removeAllChildren();

            root.add(node);
            treeChanged();
            root.getTree().expandRow(0);
          }
        });
      }
    });
  }
}
