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
package com.intellij.ui.treeStructure.filtered;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilteringTreeStructure extends AbstractTreeStructure {

  private final ElementFilter myFilter;
  private final AbstractTreeStructure myStructure;
  private final Node myRoot;

  private final Map<Object, Node> myNodeObject2Node = new HashMap<Object, Node>();

  public FilteringTreeStructure(Project project, ElementFilter filter, AbstractTreeStructure originalStructure) {
    myFilter = filter;
    myStructure = originalStructure;
    myRoot = new Node(project);
    refilter();
  }

  public void refilter() {
    myRoot.clear();
    myNodeObject2Node.clear();
    fillChildren(myRoot, getStructure().getRootElement());
  }

  private void fillChildren(Node node, Object nodeObject) {
    node.setDelegate(nodeObject);
    myNodeObject2Node.put(nodeObject, node);
    Object[] nodeChildren = getStructure().getChildElements(nodeObject);
    for (Object aNodeChildren : nodeChildren) {
      Node nodeChild = node.add(aNodeChildren);
      fillChildren(nodeChild, aNodeChildren);
      if (!myFilter.shouldBeShowing(aNodeChildren) && nodeChild.getChildren().length == 0) {
        node.remove(nodeChild);
      }
    }
  }

  public Node getVisibleNodeFor(Object nodeObject) {
    return myNodeObject2Node.get(nodeObject);
  }
  
  public class Node extends CachingSimpleNode {

    private Object myDelegate;
    private final List<Node> myChildren = new ArrayList<Node>();

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

    @Override
    public String toString() {
      return String.valueOf(getDelegate());
    }

    @Override
    public boolean isContentHighlighted() {
      if (myDelegate instanceof SimpleNode) {
        return ((SimpleNode)myDelegate).isContentHighlighted();
      }

      return false;
    }

    @Override
    public boolean isHighlightableContentNode(final PresentableNodeDescriptor kid) {
      if (myDelegate instanceof PresentableNodeDescriptor) {
        return ((PresentableNodeDescriptor)myDelegate).isHighlightableContentNode(kid);
      }

      return false;
    }



    @Override
    protected void updateFileStatus() {
      
    }

    protected void doUpdate() {
      clearColoredText();
      if (myDelegate instanceof PresentableNodeDescriptor) {
        PresentableNodeDescriptor node = (PresentableNodeDescriptor)myDelegate;
        node.update();
        apply(node.getPresentation());
      } else if (myDelegate != null) {
        NodeDescriptor descriptor = getStructure().createDescriptor(myDelegate, getParentDescriptor());
        Icon closedIcon = null;
        Icon openIcon = null;
        if (descriptor != null) {
          descriptor.update();
          closedIcon = descriptor.getClosedIcon();
          openIcon = descriptor.getOpenIcon();
        }
        setIcons(closedIcon, openIcon);
        setPlainText(myDelegate.toString());
      }
    }

    @Override
    public int getWeight() {
      if (getDelegate() instanceof SimpleNode) {
        return ((SimpleNode)getDelegate()).getWeight();
      }
      return super.getWeight();
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
      return myChildren.isEmpty() ? NO_CHILDREN : myChildren.toArray(new SimpleNode[myChildren.size()]);
    }

    public Object[] getEqualityObjects() {
      return new Object[] {myDelegate};
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
