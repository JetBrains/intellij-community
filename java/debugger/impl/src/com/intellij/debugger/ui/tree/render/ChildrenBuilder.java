package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.ui.tree.*;

import java.util.List;

public interface ChildrenBuilder {
  NodeDescriptorFactory  getDescriptorManager();

  NodeManager getNodeManager();
    
  ValueDescriptor getParentDescriptor();

  void setChildren(List<DebuggerTreeNode> children);
}
