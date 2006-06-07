/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl;

import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewPresentation;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class UsageViewTreeModelBuilder extends DefaultTreeModel {
  private final RootGroupNode myRootNode;
  private DefaultMutableTreeNode myTargetsNode;

  private UsageTarget[] myTargets;
  private UsageTargetNode[] myTargetNodes;
  private String myTargetsNodeText;

  public UsageViewTreeModelBuilder(UsageViewPresentation presentation, UsageTarget[] targets) {
    //noinspection HardCodedStringLiteral
    super(new DefaultMutableTreeNode("temp root"));
    myRootNode = new RootGroupNode();
    setRoot(myRootNode);

    myTargets = targets;
    myTargetsNodeText = presentation.getTargetsNodeText();
    if (myTargetsNodeText != null) {
      myTargetsNode = new DefaultMutableTreeNode(myTargetsNodeText);

      addTargetNodes();
    }
  }

  private void addTargetNodes() {
    if (myTargets.length == 0) return;
    myTargetNodes = new UsageTargetNode[myTargets.length];
    myTargetsNode.removeAllChildren();
    for (int i = 0; i < myTargets.length; i++) {
      UsageTarget target = myTargets[i];
      UsageTargetNode targetNode = new UsageTargetNode(target, this);
      myTargetsNode.add(targetNode);
      myTargetNodes[i] = targetNode;
    }
    myRootNode.addNode(myTargetsNode);
  }

  public UsageNode getFirstUsageNode() {
    return (UsageNode)getFirstChildOfType(myRootNode, UsageNode.class);
  }

  private static TreeNode getFirstChildOfType(TreeNode parent, final Class type) {
    final int childCount = parent.getChildCount();
    for (int idx = 0; idx < childCount; idx++) {
      final TreeNode child = parent.getChildAt(idx);
      if (type.isAssignableFrom(child.getClass())) {
        return child;
      }
      final TreeNode firstChildOfType = getFirstChildOfType(child, type);
      if (firstChildOfType != null) {
        return firstChildOfType;
      }
    }
    return null;
  }

  public boolean areTargetsValid() {
    if (myTargetNodes == null) return true;
    for (UsageTargetNode targetNode : myTargetNodes) {
      if (!targetNode.isValid()) return false;
    }
    return true;
  }

  public void reset() {
    myRootNode.removeAllChildren();
    if (myTargetsNodeText != null && myTargets.length > 0) {
      addTargetNodes();
    }
  }

  private class RootGroupNode extends GroupNode {
    public RootGroupNode() {
      super(null, 0, UsageViewTreeModelBuilder.this);
    }

    public void addNode(DefaultMutableTreeNode node) {
      myTreeModel.insertNodeInto(node, this, getNodeInsertionIndex(node));
    }
  }
}
