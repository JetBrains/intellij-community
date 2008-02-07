/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.ui.tree.TreeInplaceEditor;

import javax.swing.*;
import javax.swing.tree.TreeNode;

public abstract class DebuggerTreeInplaceEditor extends TreeInplaceEditor {
  private final DebuggerTreeNodeImpl myNode;

  protected Project getProject() {
    return myNode.getTree().getProject();
  }

  public DebuggerTreeInplaceEditor(DebuggerTreeNodeImpl node) {
    myNode = node;
  }

  protected TreeNode[] getNodePath() {
    return myNode.getPath();
  }

  protected JTree getTree() {
    return myNode.getTree();
  }

}
