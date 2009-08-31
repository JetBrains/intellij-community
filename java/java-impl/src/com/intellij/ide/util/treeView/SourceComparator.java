package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;

import java.util.Comparator;

public class SourceComparator implements Comparator<NodeDescriptor>{
  public static final SourceComparator INSTANCE = new SourceComparator();

  private SourceComparator() {
  }

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
    int index1 = nodeDescriptor1.getIndex();
    int index2 = nodeDescriptor2.getIndex();
    if (index1 == index2) return 0;
    return index1 < index2 ? -1 : +1;
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