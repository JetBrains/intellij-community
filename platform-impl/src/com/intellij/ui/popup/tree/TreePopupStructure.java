/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.tree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.popup.util.ElementFilter;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TreePopupStructure extends AbstractTreeStructure {

  private ElementFilter myFilter;
  private AbstractTreeStructure myStructure;
  private Node myRoot;

  public TreePopupStructure(Project project, ElementFilter filter, AbstractTreeStructure originalStructure) {
    myFilter = filter;
    myStructure = originalStructure;
    myRoot = new Node(project);
    refilter();
  }

  public void refilter() {
    myRoot.clear();
    fillChildren(myRoot, getStructure().getRootElement());
  }

  private void fillChildren(Node node, Object nodeObject) {
    node.setDelegate(nodeObject);
    Object[] nodeChildren = getStructure().getChildElements(nodeObject);
    for (Object aNodeChildren : nodeChildren) {
      Node nodeChild = node.add(aNodeChildren);
      fillChildren(nodeChild, aNodeChildren);
      if (!myFilter.shouldBeShowing(aNodeChildren) && nodeChild.getChildren().length == 0) {
        node.remove(nodeChild);
      }
    }
  }

  public class Node extends CachingSimpleNode {

    private Object myDelegate;
    private List<Node> myChildren = new ArrayList<Node>();

    public Node(Project project) {
      super(project, null);
    }

    public Node(SimpleNode parent, Object delegate) {
      super(parent);
      myDelegate = delegate;
    }

    public void setDelegate(Object delegate) {
      myDelegate = delegate;
    }

    public Object getDelegate() {
      return myDelegate;
    }

    protected void doUpdate() {
      clearColoredText();
      if (myDelegate instanceof SimpleNode) {
        SimpleNode node = (SimpleNode)myDelegate;
        node.update();

        ColoredFragment[] text = node.getColoredText();
        for (ColoredFragment each : text) {
          addColoredFragment(each);
        }

        setIcons(node.getClosedIcon(), node.getOpenIcon());
      } else if (myDelegate != null) {
        setPlainText(myDelegate.toString());
        NodeDescriptor descriptor = getStructure().createDescriptor(myDelegate, getParentDescriptor());
        Icon closedIcon = null;
        Icon openIcon = null;
        if (descriptor != null) {
          descriptor.update();
          closedIcon = descriptor.getClosedIcon();
          openIcon = descriptor.getOpenIcon();
        }
        setIcons(closedIcon, openIcon);
      }
    }

    public void clear() {
      cleanUpCache();
      myChildren.clear();
    }

    public Node add(Object child) {
      Node childNode = new Node(this, child);
      myChildren.add(childNode);
      return childNode;
    }

    public void remove(Node node) {
      myChildren.remove(node);
    }

    protected SimpleNode[] buildChildren() {
      return myChildren.toArray(new SimpleNode[myChildren.size()]);
    }

    public Object[] getEqualityObjects() {
      return NONE;
    }
  }


  private AbstractTreeStructure getStructure() {
    return myStructure;
  }

  public Object getRootElement() {
    return myRoot;
  }

  public Object[] getChildElements(Object element) {
    return ((Node) element).getChildren();
  }

  public Object getParentElement(Object element) {
    return ((Node) element).getParent();
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return (Node)element;
  }

  public void commit() {
    getStructure().commit();
  }

  public boolean hasSomethingToCommit() {
    return getStructure().hasSomethingToCommit();
  }

}