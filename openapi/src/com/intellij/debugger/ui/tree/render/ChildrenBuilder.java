package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.ui.tree.*;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 17, 2004
 * Time: 7:47:10 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ChildrenBuilder {
  NodeDescriptorFactory    getDescriptorManager();
  NodeManager              getNodeManager      ();
    
  ValueDescriptor          getParentDescriptor ();

  void setChildren(List<DebuggerTreeNode> children);

}
