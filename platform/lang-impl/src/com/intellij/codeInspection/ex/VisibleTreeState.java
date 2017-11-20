// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
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
    final State expandedNode;
    if (node instanceof InspectionConfigTreeNode.Tool) {
      expandedNode = new State(((InspectionConfigTreeNode.Tool)node).getKey().toString());
    }
    else {
      final StringBuilder buf = new StringBuilder();
      while (node.getParent() != null) {
        buf.append(((InspectionConfigTreeNode.Group)node).getGroupName());
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


  public static class State implements Comparable<State> {
    @Tag("id")
    public String myKey;

    public State(String key) {
      myKey = key;
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
      return myKey != null ? myKey.hashCode() : 0;
    }

    @Override
    public int compareTo(State state) {
      return myKey.compareTo(state.myKey);
    }
  }
}
