package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class InspectDebuggerTree extends DebuggerTree{
  private NodeDescriptorImpl myInspectDescriptor;

  public InspectDebuggerTree(Project project) {
    super(project);
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
