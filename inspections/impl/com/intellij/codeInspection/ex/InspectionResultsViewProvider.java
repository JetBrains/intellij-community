/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class InspectionResultsViewProvider {

  public abstract boolean hasReportedProblems(final InspectionTool tool);

  public abstract InspectionTreeNode [] getContents(final InspectionTool tool);

  @Nullable
  public abstract QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree);

  protected static RefElementNode addNodeToParent(Descriptor descriptor, InspectionTool tool, InspectionPackageNode packageNode) {
    final Set<InspectionTreeNode> children = new HashSet<InspectionTreeNode>();
    TreeUtil.traverseDepth(packageNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        children.add((InspectionTreeNode)node);
        return true;
      }
    });
    RefElementNode nodeToAdd = descriptor.createNode(tool);
    boolean firstLevel = true;
    RefElementNode prevNode = null;
    while (true) {
      RefElementNode currentNode = firstLevel ? nodeToAdd : descriptor.createNode(tool);
      for (InspectionTreeNode node : children) {
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          if (Comparing.equal(refElementNode.getUserObject(), descriptor.getUserObject())) {
            if (firstLevel) {
              return refElementNode;
            }
            else {
              refElementNode.add(prevNode);
              return nodeToAdd;
            }
          }
        }
      }
      if (!firstLevel) {
        currentNode.add(prevNode);
      }
      final Descriptor owner = descriptor.getOwner();
      if (owner == null) {
        packageNode.add(currentNode);
        return nodeToAdd;
      }
      descriptor = owner;
      prevNode = currentNode;
      firstLevel = false;
    }
  }

  protected static interface Descriptor {
    @Nullable
    Descriptor getOwner();
    
    RefElementNode createNode(InspectionTool tool);

    Object getUserObject();
  }
}