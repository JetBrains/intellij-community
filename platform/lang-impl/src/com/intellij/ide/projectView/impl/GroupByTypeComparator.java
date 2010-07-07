/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;

import java.util.Collection;
import java.util.Comparator;

/**
 * @author cdr
 */
public class GroupByTypeComparator implements Comparator<NodeDescriptor> {
  private ProjectView myProjectView;
  private String myPaneId;
  private boolean myForceSortByType;

  public GroupByTypeComparator(final ProjectView projectView, final String paneId) {
    myProjectView = projectView;
    myPaneId = paneId;
  }

  public GroupByTypeComparator(final boolean forceSortByType) {
    myForceSortByType = forceSortByType;
  }

  public int compare(NodeDescriptor descriptor1, NodeDescriptor descriptor2) {
    if (!isSortByType() && descriptor1 instanceof ProjectViewNode && ((ProjectViewNode) descriptor1).isSortByFirstChild()) {
      final Collection<AbstractTreeNode> children = ((ProjectViewNode)descriptor1).getChildren();
      if (!children.isEmpty()) {
        descriptor1 = children.iterator().next();
        descriptor1.update();
      }
    }
    if (!isSortByType() && descriptor2 instanceof ProjectViewNode && ((ProjectViewNode) descriptor2).isSortByFirstChild()) {
      final Collection<AbstractTreeNode> children = ((ProjectViewNode)descriptor2).getChildren();
      if (!children.isEmpty()) {
        descriptor2 = children.iterator().next();
        descriptor2.update();
      }
    }

    if (descriptor1 instanceof ProjectViewNode && descriptor2 instanceof ProjectViewNode) {
      ProjectViewNode node1 = (ProjectViewNode) descriptor1;
      ProjectViewNode node2 = (ProjectViewNode) descriptor2;
      int typeWeight1 = node1.getTypeSortWeight(isSortByType());
      int typeWeight2 = node2.getTypeSortWeight(isSortByType());
      if (typeWeight1 != 0 && typeWeight2 == 0) {
        return -1;
      }
      if (typeWeight1 == 0 && typeWeight2 != 0) {
        return 1;
      }
      if (typeWeight1 != 0 && typeWeight2 != typeWeight1) {
        return typeWeight1 - typeWeight2;
      }

      if (isSortByType()) {
        final Comparable typeSortKey1 = node1.getTypeSortKey();
        final Comparable typeSortKey2 = node2.getTypeSortKey();
        if (typeSortKey1 != null && typeSortKey2 != null) {
          final int result = typeSortKey1.compareTo(typeSortKey2);
          if (result != 0) return result;
        }
      } else {
        final Comparable typeSortKey1 = node1.getSortKey();
        final Comparable typeSortKey2 = node2.getSortKey();
        if (typeSortKey1 != null && typeSortKey2 != null) {
          final int result = typeSortKey1.compareTo(typeSortKey2);
          if (result != 0) return result;
        }
      }

      if (isAbbreviateQualifiedNames()) {
        String key1 = node1.getQualifiedNameSortKey();
        String key2 = node2.getQualifiedNameSortKey();
        if (key1 != null && key2 != null) {
          return key1.compareToIgnoreCase(key2);
        }
      }
    }
    if (descriptor1 == null) return -1; 
    if (descriptor2 == null) return 1;
    return AlphaComparator.INSTANCE.compare(descriptor1, descriptor2);
  }

  protected boolean isSortByType() {
    if (myProjectView != null) {
      return myProjectView.isSortByType(myPaneId);
    }
    return myForceSortByType;
  }

  private boolean isAbbreviateQualifiedNames() {
    return myProjectView != null && myProjectView.isAbbreviatePackageNames(myPaneId);
  }

}
