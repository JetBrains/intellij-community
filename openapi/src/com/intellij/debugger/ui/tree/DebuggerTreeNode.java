package com.intellij.debugger.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.debugger.ui.tree.render.NodeRenderer;

import javax.swing.tree.MutableTreeNode;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DebuggerTreeNode extends MutableTreeNode{
  DebuggerTreeNode getParent();

  NodeDescriptor getDescriptor();

  Project getProject();

  void setRenderer(NodeRenderer renderer);
}
