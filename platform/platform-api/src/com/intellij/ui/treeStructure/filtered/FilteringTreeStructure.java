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
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class FilteringTreeStructure extends AbstractTreeStructure {
  private final ElementFilter<Object> myFilter;
  private final AbstractTreeStructure myBaseStructure;
  protected final FilteringNode myRoot;
  protected final HashSet<FilteringNode> myLeaves = new HashSet<FilteringNode>();
  private Map<FilteringNode, List<FilteringNode>> myNodesCache = new HashMap<FilteringNode, List<FilteringNode>>();

  protected enum State {UNKNOWN, VISIBLE, HIDDEN}

  private final Map<Object, FilteringNode> myDescriptors2Nodes = new HashMap<Object, FilteringNode>();

  public FilteringTreeStructure(ElementFilter filter, AbstractTreeStructure originalStructure) {
    this(filter, originalStructure, true);
  }

  public FilteringTreeStructure(ElementFilter filter, AbstractTreeStructure originalStructure, boolean initNow) {
    //noinspection unchecked
    myFilter = filter;
    myBaseStructure = originalStructure;
    myRoot = new FilteringNode(null, myBaseStructure.getRootElement());
    if (initNow) {
      rebuild();
    }
  }

  public void rebuild() {
    myLeaves.clear();
    myNodesCache.clear();
    myDescriptors2Nodes.clear();
    addToCache(myRoot, false);
  }

  private void addToCache(FilteringNode node, boolean duplicate) {
    Object delegate = node.getDelegate();
    Object[] delegates = myBaseStructure.getChildElements(delegate);
    if (delegates == null || delegates.length == 0 || duplicate) {
      myLeaves.add(node);
    } else {
      ArrayList<FilteringNode> nodes = new ArrayList<FilteringNode>(delegates.length);
      for (Object d : delegates) {
        FilteringNode n = new FilteringNode(node, d);
        boolean isDuplicate = myDescriptors2Nodes.containsKey(d);
        myDescriptors2Nodes.put(d, n);
        nodes.add(n);
        addToCache(n, isDuplicate);
      }
      myNodesCache.put(node, nodes);
    }
  }

  public void refilter() {
    setUnknown(myRoot);
    for (FilteringNode node : myLeaves) {
      State state = getState(node);
      while (node != null && node.state != State.VISIBLE) {
        if (node.state != state) {
          node.state = state;
          node = node.getParentNode();
          if (node != null && state == State.HIDDEN) {
            state = getState(node);
          }
        } else {
          break;
        }
      }
    }
  }

  private State getState(@NotNull FilteringNode node) {
    return myFilter.shouldBeShowing(node.getDelegate()) ? State.VISIBLE : State.HIDDEN;
  }

  private void setUnknown(FilteringNode node) {
    node.state = State.UNKNOWN;
    List<FilteringNode> nodes = myNodesCache.get(node);
    if (nodes != null) {
      for (FilteringNode n : nodes) {
        setUnknown(n);
      }
    }
  }

  public FilteringNode getVisibleNodeFor(Object nodeObject) {
    return myDescriptors2Nodes.get(nodeObject);
  }

  public Object getRootElement() {
    return myRoot;
  }

  public Object[] getChildElements(Object element) {
    return ((FilteringNode) element).getChildren();
  }

  public Object getParentElement(Object element) {
    return ((FilteringNode) element).getParent();
  }

  @Override
  public boolean isAlwaysLeaf(Object element) {
    if (element instanceof FilteringNode) {
      return ((FilteringNode)element).isAlwaysLeaf();
    }
    return false;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return (FilteringNode)element;
  }

  public void commit() {
    myBaseStructure.commit();
  }

  public boolean hasSomethingToCommit() {
    return myBaseStructure.hasSomethingToCommit();
  }

  public class FilteringNode extends SimpleNode {
    private Object myDelegate;
    private State state = State.VISIBLE;

    public FilteringNode(SimpleNode parent, Object delegate) {
      super(parent);
      myDelegate = delegate;
    }

    public void setDelegate(Object delegate) {
      myDelegate = delegate;
    }

    public FilteringNode getParentNode() {
      return (FilteringNode)getParent();
    }

    public Object getDelegate() {
      return myDelegate;
    }

    public List<FilteringNode> children() {
      List<FilteringNode> nodes = myNodesCache.get(this);
      return nodes == null ? Collections.<FilteringNode>emptyList() : nodes;
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
      // DO NOTHING
    }

    protected void doUpdate() {
      clearColoredText();
      if (myDelegate instanceof PresentableNodeDescriptor) {
        PresentableNodeDescriptor node = (PresentableNodeDescriptor)myDelegate;
        node.update();
        apply(node.getPresentation());
      } else if (myDelegate != null) {
        NodeDescriptor descriptor = myBaseStructure.createDescriptor(myDelegate, getParentDescriptor());
        descriptor.update();
        setIcons(descriptor.getClosedIcon(), descriptor.getOpenIcon());
        setPlainText(myDelegate.toString());
      }
    }

    @Override
    public SimpleNode[] getChildren() {
      List<FilteringNode> nodes = myNodesCache.get(this);
      if (nodes == null) {
        return SimpleNode.NO_CHILDREN;
      }

      ArrayList<FilteringNode> result = new ArrayList<FilteringNode>();
      for (FilteringNode node : nodes) {
        if (node.state == State.VISIBLE) {
          result.add(node);
        }
      }
      return result.toArray(new FilteringNode[result.size()]);
    }

    @Override
    public int getWeight() {
      if (getDelegate() instanceof SimpleNode) {
        return ((SimpleNode)getDelegate()).getWeight();
      }
      return super.getWeight();
    }

    public Object[] getEqualityObjects() {
      return NONE;
    }

    @Override
    public boolean isAlwaysShowPlus() {
      if (myDelegate instanceof SimpleNode) {
        return ((SimpleNode)myDelegate).isAlwaysShowPlus();
      }

      return super.isAlwaysShowPlus();
    }

    @Override
    public boolean isAlwaysLeaf() {
      if (myDelegate instanceof SimpleNode) {
        return ((SimpleNode)myDelegate).isAlwaysLeaf();
      }

      return super.isAlwaysLeaf();
    }
  }
}
