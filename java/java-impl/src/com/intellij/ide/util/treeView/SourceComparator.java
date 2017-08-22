/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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