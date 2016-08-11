/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.codeInspection.ex;

import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

/**
 * User: anna
 * Date: Dec 18, 2004
 */
@Tag("profile-state")
public class VisibleTreeState{
  @Tag("expanded-state")
  @AbstractCollection(surroundWithTag = false, elementTag = "expanded", elementValueAttribute = "path", elementTypes = {State.class})
  public TreeSet<State> myExpandedNodes = new TreeSet<>();

  @Tag("selected-state")
  @AbstractCollection(surroundWithTag = false, elementTag = "selected", elementValueAttribute = "path", elementTypes = {State.class})
  public TreeSet<State> mySelectedNodes = new TreeSet<>();

  public VisibleTreeState(VisibleTreeState src) {
    myExpandedNodes.addAll(src.myExpandedNodes);
    mySelectedNodes.addAll(src.mySelectedNodes);
  }

  public VisibleTreeState() {
  }

  public void expandNode(InspectionConfigTreeNode node) {
    myExpandedNodes.add(getState(node));
  }

  public void collapseNode(InspectionConfigTreeNode node) {
    myExpandedNodes.remove(getState(node));
  }

  public void restoreVisibleState(Tree tree) {
    ArrayList<TreePath> pathsToExpand = new ArrayList<>();
    ArrayList<TreePath> toSelect = new ArrayList<>();
    traverseNodes((DefaultMutableTreeNode)tree.getModel().getRoot(), pathsToExpand, toSelect);
    TreeUtil.restoreExpandedPaths(tree, pathsToExpand);
    if (toSelect.isEmpty()) {
      TreeUtil.selectFirstNode(tree);
    }
    else {
      for (final TreePath aToSelect : toSelect) {
        TreeUtil.selectPath(tree, aToSelect);
      }
    }
  }

  private void traverseNodes(final DefaultMutableTreeNode root, List<TreePath> pathsToExpand, List<TreePath> toSelect) {
    final State state = getState((InspectionConfigTreeNode)root);
    final TreeNode[] rootPath = root.getPath();
    if (mySelectedNodes.contains(state)) {
      toSelect.add(new TreePath(rootPath));
    }
    if (myExpandedNodes.contains(state)) {
      pathsToExpand.add(new TreePath(rootPath));
    }
    for (int i = 0; i < root.getChildCount(); i++) {
      traverseNodes((DefaultMutableTreeNode)root.getChildAt(i), pathsToExpand, toSelect);
    }
  }

  public void saveVisibleState(Tree tree) {
    myExpandedNodes.clear();
    final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)tree.getModel().getRoot();
    Enumeration<TreePath> expanded = tree.getExpandedDescendants(new TreePath(rootNode.getPath()));
    if (expanded != null) {
      while (expanded.hasMoreElements()) {
        final TreePath treePath = expanded.nextElement();
        final InspectionConfigTreeNode node = (InspectionConfigTreeNode)treePath.getLastPathComponent();
        myExpandedNodes.add(getState(node));
      }
    }

    setSelectionPaths(tree.getSelectionPaths());
  }

  private static State getState(InspectionConfigTreeNode node) {
    Descriptor descriptor = node.getDefaultDescriptor();
    final State expandedNode;
    if (descriptor != null) {
      expandedNode = new State(descriptor);
    }
    else {
      final StringBuilder buf = new StringBuilder();
      while (node.getParent() != null) {
        buf.append(node.getGroupName());
        node = (InspectionConfigTreeNode)node.getParent();
      }
      expandedNode = new State(buf.toString());
    }
    return expandedNode;
  }

  public void setSelectionPaths(final TreePath[] selectionPaths) {
    mySelectedNodes.clear();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        final InspectionConfigTreeNode node = (InspectionConfigTreeNode)selectionPath.getLastPathComponent();
        mySelectedNodes.add(getState(node));
      }
    }
  }


  public static class State implements Comparable{
    @Tag("id")
    public String myKey;
    Descriptor myDescriptor;

    public State(String key) {
      myKey = key;
    }

    public State(Descriptor descriptor) {
      myKey = descriptor.toString();
      myDescriptor = descriptor;
    }

    //readExternal
    public State(){
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;

      if (myKey != null ? !myKey.equals(state.myKey) : state.myKey != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myKey != null ? myKey.hashCode() : 0;
      result = 31 * result + (myDescriptor != null ? myDescriptor.hashCode() : 0);
      return result;
    }

    @Override
    public int compareTo(Object o) {
      if (!(o instanceof State)) return -1;
      final State other = (State)o;
      if (myKey.equals(other.myKey)) {
        if (myDescriptor != null && other.myDescriptor != null) {
          final String scope1 = myDescriptor.getScopeName();
          final String scope2 = other.myDescriptor.getScopeName();
          return scope1.compareTo(scope2);
        }
      }
      return myKey.compareTo(other.myKey);
    }
  }
}
