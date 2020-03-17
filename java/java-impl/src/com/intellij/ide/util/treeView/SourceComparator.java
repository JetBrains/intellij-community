// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;

import java.util.Comparator;

public final class SourceComparator implements Comparator<NodeDescriptor<?>> {
  public static final SourceComparator INSTANCE = new SourceComparator();

  private SourceComparator() {
  }

  @Override
  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    int weight1 = getWeight(nodeDescriptor1);
    int weight2 = getWeight(nodeDescriptor2);
    if (weight1 != weight2) {
      return weight1 - weight2;
    }
    if (!(nodeDescriptor1.getParentDescriptor() instanceof ProjectViewProjectNode)){
      if (nodeDescriptor1 instanceof PsiDirectoryNode || nodeDescriptor1 instanceof PsiFileNode){
        return nodeDescriptor1.toString().compareToIgnoreCase(nodeDescriptor2.toString());
      }
      if (nodeDescriptor1 instanceof ClassTreeNode && nodeDescriptor2 instanceof ClassTreeNode){
        if (((ClassTreeNode)nodeDescriptor1).isTopLevel()){
          return nodeDescriptor1.toString().compareToIgnoreCase(nodeDescriptor2.toString());
        }
      }
    }
    return Integer.compare(nodeDescriptor1.getIndex(), nodeDescriptor2.getIndex());
  }

  private static int getWeight(NodeDescriptor descriptor) {
    if (descriptor instanceof PsiDirectoryNode) {
      return ((PsiDirectoryNode)descriptor).isFQNameShown() ? 7 : 0;
    }
    else {
      return 2;
    }
  }
}