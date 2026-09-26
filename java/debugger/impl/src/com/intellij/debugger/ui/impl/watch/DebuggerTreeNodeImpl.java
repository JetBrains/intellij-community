// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;

import javax.swing.tree.DefaultMutableTreeNode;

public class DebuggerTreeNodeImpl extends DefaultMutableTreeNode implements DebuggerTreeNode, NodeDescriptorProvider {
  public DebuggerTreeNodeImpl(NodeDescriptor descriptor) {
    super(descriptor);
  }

  /**
   * @deprecated Use {@link #DebuggerTreeNodeImpl(NodeDescriptor)}.
   */
  @SuppressWarnings("removal")
  @Deprecated(forRemoval = true)
  public DebuggerTreeNodeImpl(DebuggerTree ignoredTree, NodeDescriptor descriptor) {
    this(descriptor);
  }

  @Override
  public DebuggerTreeNodeImpl getParent() {
    return (DebuggerTreeNodeImpl)super.getParent();
  }

  @Override
  public NodeDescriptorImpl getDescriptor() {
    return (NodeDescriptorImpl)getUserObject();
  }

  @Override
  public Project getProject() {
    NodeDescriptorImpl descriptor = getDescriptor();
    return descriptor instanceof ValueDescriptorImpl valueDescriptor ? valueDescriptor.getProject() : null;
  }
}
