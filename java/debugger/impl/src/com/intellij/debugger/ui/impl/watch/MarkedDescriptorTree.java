package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.impl.descriptors.data.DescriptorKey;
import com.intellij.debugger.ui.tree.NodeDescriptor;

import java.util.HashMap;
import java.util.Map;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class MarkedDescriptorTree {
  private final HashMap<NodeDescriptor, Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor>> myChildrenMap = new HashMap<NodeDescriptor, Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor>>();
  private final Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor> myRootChildren = new com.intellij.util.containers.HashMap<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor>();

  public <T extends NodeDescriptor> void addChild(NodeDescriptor parent, T child, DescriptorKey<T> key) {
    Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor> children;

    if(parent == null) {
      children = myRootChildren;
    }
    else {
      children = myChildrenMap.get(parent);
      if(children == null) {
        children = new com.intellij.util.containers.HashMap<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor>();
        myChildrenMap.put(parent, children);
      }
    }
    children.put(key, child);
  }

  public <T extends NodeDescriptor> T getChild(NodeDescriptor parent, DescriptorKey<T> key) {
    if(parent == null) {
      return (T)myRootChildren.get(key);
    }
    final Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor> map = myChildrenMap.get(parent);
    return (T)(map != null ? map.get(key) : null);
  }

  public void clear() {
    myChildrenMap.clear();
    myRootChildren.clear();
  }
}
