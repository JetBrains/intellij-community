// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure.filtered;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class FilteringTreeStructure extends AbstractTreeStructure {
  private final ElementFilter<Object> myFilter;
  private final AbstractTreeStructure myBaseStructure;
  protected final FilteringNode myRoot;
  protected final HashSet<FilteringNode> myLeaves = new HashSet<>();
  private final Map<FilteringNode, List<FilteringNode>> myNodesCache = new HashMap<>();

  protected enum State {UNKNOWN, VISIBLE, HIDDEN}

  private final Map<Object, FilteringNode> myDescriptors2Nodes = new HashMap<>();

  public FilteringTreeStructure(@NotNull ElementFilter filter, @NotNull AbstractTreeStructure originalStructure) {
    this(filter, originalStructure, true);
  }

  public FilteringTreeStructure(@NotNull ElementFilter filter, @NotNull AbstractTreeStructure originalStructure, boolean initNow) {
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
    if (delegates.length == 0 || duplicate) {
      myLeaves.add(node);
    } else {
      ArrayList<FilteringNode> nodes = new ArrayList<>(delegates.length);
      for (Object d : delegates) {
        boolean isDuplicate = myDescriptors2Nodes.containsKey(d);
        if (!isDuplicate) {
          FilteringNode n = new FilteringNode(node, d);
          myDescriptors2Nodes.put(d, n);
          nodes.add(n);
        }
      }
      myNodesCache.put(node, nodes);
      for (FilteringNode n : nodes) {
        addToCache(n, false);
      }
      if (nodes.isEmpty()) {
        myLeaves.add(node);
      }
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

  public @Unmodifiable List<FilteringNode> getVisibleLeaves() {
    return ContainerUtil.filter(myLeaves, node -> node.state == State.VISIBLE);
  }

  private State getState(@NotNull FilteringNode node) {
    return myFilter.shouldBeShowing(node.getDelegate()) ? State.VISIBLE : State.HIDDEN;
  }

  private void setUnknown(FilteringNode node) {
    if (node.state == State.UNKNOWN) return;
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

  @Override
  public @NotNull FilteringNode getRootElement() {
    return myRoot;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    return ((FilteringNode) element).getChildren();
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    return ((FilteringNode) element).getParent();
  }

  @Override
  public boolean isAlwaysLeaf(@NotNull Object element) {
    return element instanceof FilteringNode && ((FilteringNode)element).isAlwaysLeaf();
  }

  @Override
  public boolean isToBuildChildrenInBackground(@NotNull Object element) {
    return myBaseStructure.isToBuildChildrenInBackground(element);
  }

  @Override
  public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    return element instanceof FilteringNode ? (FilteringNode)element : new FilteringNode((SimpleNode)parentDescriptor, element);
  }

  @Override
  public void commit() {
    myBaseStructure.commit();
  }

  @Override
  public boolean hasSomethingToCommit() {
    return myBaseStructure.hasSomethingToCommit();
  }

  @Override
  public @NotNull ActionCallback asyncCommit() {
    return myBaseStructure.asyncCommit();
  }

  public FilteringNode createFilteringNode(Object delegate) {
    return new FilteringNode(null, delegate);
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
      return nodes == null ? Collections.emptyList() : nodes;
    }

    @Override
    public String toString() {
      return String.valueOf(getDelegate());
    }

    @Override
    public boolean isContentHighlighted() {
      return myDelegate instanceof SimpleNode && ((SimpleNode)myDelegate).isContentHighlighted();
    }

    @Override
    public boolean isHighlightableContentNode(final @NotNull PresentableNodeDescriptor kid) {
      return myDelegate instanceof PresentableNodeDescriptor && ((PresentableNodeDescriptor<?>)myDelegate).isHighlightableContentNode(kid);
    }

    @Override
    protected void doUpdate(@NotNull PresentationData presentation) {
      presentation.clearText();
      if (myDelegate instanceof PresentableNodeDescriptor<?> node) {
        node.update();
        presentation.applyFrom(node.getPresentation());
      } else if (myDelegate != null) {
        NodeDescriptor<?> descriptor = myBaseStructure.createDescriptor(myDelegate, getParentDescriptor());
        descriptor.update();
        presentation.setIcon(descriptor.getIcon());
        presentation.setPresentableText(myDelegate.toString());
      }
    }

    @Override
    public SimpleNode @NotNull [] getChildren() {
      List<FilteringNode> nodes = myNodesCache.get(this);
      if (nodes == null) {
        return myDelegate instanceof SimpleNode ? ContainerUtil.map(((SimpleNode)myDelegate).getChildren(),
                                                                    node -> new FilteringNode(this, node), NO_CHILDREN) : NO_CHILDREN;
      }

      ArrayList<FilteringNode> result = new ArrayList<>();
      for (FilteringNode node : nodes) {
        if (node.state == State.VISIBLE) {
          result.add(node);
        }
      }
      return result.toArray(new FilteringNode[0]);
    }

    @Override
    public int getWeight() {
      if (getDelegate() instanceof NodeDescriptor) {
        return ((NodeDescriptor<?>)getDelegate()).getWeight();
      }
      return super.getWeight();
    }

    @Override
    public Object @NotNull [] getEqualityObjects() {
      return new Object[]{myDelegate};
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
