package com.intellij.debugger.actions;

import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class AutoRendererAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    final DebuggerTreeNodeImpl[] selectedNodes = DebuggerAction.getSelectedNodes(e.getDataContext());

    if(debuggerContext != null && debuggerContext.getDebugProcess() != null) {
      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
          public void threadAction() {
            for (int i = 0; i < selectedNodes.length; i++) {
              DebuggerTreeNodeImpl selectedNode = selectedNodes[i];
              NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
              if (descriptor instanceof ValueDescriptorImpl) {
                ((ValueDescriptorImpl) descriptor).setRenderer(null);
                selectedNode.calcRepresentation();
              }
            }
          }
        });
    }
  }
}
