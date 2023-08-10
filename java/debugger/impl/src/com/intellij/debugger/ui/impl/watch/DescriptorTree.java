// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.iterateBackward;

public class DescriptorTree {
  private final HashMap<NodeDescriptor, List<NodeDescriptor>> myChildrenMap = new HashMap<>();
  private final List<NodeDescriptor> myRootChildren = new ArrayList<>();
  private final boolean myInitial;
  private int myFrameCount = -1;
  private int myFrameIndex = -1;

  public DescriptorTree() {
    this(false);
  }

  public DescriptorTree(boolean isInitial) {
    myInitial = isInitial;
  }

  public void clear() {
    myChildrenMap.clear();
    myRootChildren.clear();
  }

  public boolean frameIdEquals(final int frameCount, final int frameIndex) {
    return myFrameCount == frameCount && myFrameIndex == frameIndex;
  }

  public void setFrameId(final int frameCount, final int frameIndex) {
    myFrameIndex = frameIndex;
    myFrameCount = frameCount;
  }

  public void addChild(NodeDescriptor parent, NodeDescriptor child) {
    List<NodeDescriptor> children;

    if (parent == null) {
      children = myRootChildren;
    }
    else {
      children = myChildrenMap.computeIfAbsent(parent, c -> new ArrayList<>());
    }
    children.add(child);
    if (myInitial && child instanceof LocalVariableDescriptorImpl) {
      ((LocalVariableDescriptorImpl)child).setNewLocal(false);
    }
  }

  public List<NodeDescriptor> getChildren(NodeDescriptor parent) {
    if (parent == null) {
      return myRootChildren;
    }

    List<NodeDescriptor> children = myChildrenMap.get(parent);
    return children != null ? children : Collections.emptyList();
  }

  public void dfst(DFSTWalker walker) {
    ArrayDeque<Pair<NodeDescriptor, NodeDescriptor>> stack =
      StreamEx.of(myRootChildren).map(e -> new Pair<NodeDescriptor, NodeDescriptor>(null, e)).toCollection(ArrayDeque::new);
    while (!stack.isEmpty()) {
      Pair<NodeDescriptor, NodeDescriptor> nodeInfo = stack.pop();
      NodeDescriptor child = nodeInfo.second;
      walker.visit(nodeInfo.first, child);
      List<NodeDescriptor> list = myChildrenMap.get(child);
      if (list != null) {
        iterateBackward(ContainerUtil.map(list, e -> new Pair<>(child, e))).forEach(stack::push);
      }
    }
  }

  public interface DFSTWalker {
    void visit(NodeDescriptor parent, NodeDescriptor child);
  }
}
