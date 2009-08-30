package com.intellij.debugger.ui.tree.actions;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.sun.jdi.*;

import java.util.Enumeration;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ShowAllAs extends AnAction {
  private final NodeRenderer myRenderer;

  public ShowAllAs(NodeRenderer renderer) {
    myRenderer = renderer;
  }

  private boolean isPrimitiveArray(DebuggerTreeNode selectedNode) {
    try {
      if(selectedNode.getDescriptor() instanceof ValueDescriptor) {
        ValueDescriptor valueDescriptor = ((ValueDescriptor)selectedNode.getDescriptor());
        if(valueDescriptor.isArray()) {
          ArrayReference arrayReference = ((ArrayReference)valueDescriptor.getValue());
          Type componentType = ((ArrayType)arrayReference.type()).componentType();
          if(componentType instanceof PrimitiveType) {
            if(componentType instanceof ByteType ||
               componentType instanceof ShortType ||
               componentType instanceof IntegerType ||
               componentType instanceof LongType) {
              return true;
            }
          }
        }
      }
    }
    catch (ClassNotLoadedException e) {
    }
    return false;
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNode selectedNode = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).getSelectedNode(e.getDataContext());
    e.getPresentation().setVisible(myRenderer != null && selectedNode != null && isPrimitiveArray(selectedNode));
  }

  public void actionPerformed(AnActionEvent e) {
    DebuggerTreeNode selectedNode = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).getSelectedNode(e.getDataContext());
    if(selectedNode == null) return;
    
    if(!isPrimitiveArray(selectedNode)) return;

    final DebuggerContext debuggerContext = DebuggerUtils.getInstance().getDebuggerContext(e.getDataContext());
    if(debuggerContext == null || debuggerContext.getDebugProcess() == null) return;

    for(Enumeration children = selectedNode.children(); children.hasMoreElements(); ) {
      final DebuggerTreeNode child = (DebuggerTreeNode)children.nextElement();
      if(child.getDescriptor() instanceof ValueDescriptor) {
        debuggerContext.getDebugProcess().getManagerThread().invokeCommand(new SuspendContextCommand() {
          public SuspendContext getSuspendContext() {
            return debuggerContext.getSuspendContext();
          }

          public void action() {
            child.setRenderer(myRenderer);
          }

          public void commandCancelled() {
          }
        });
      }
    }
  }
}
