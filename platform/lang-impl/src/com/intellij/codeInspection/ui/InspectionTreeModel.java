// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.offlineViewer.OfflineDescriptorResolveResult;
import com.intellij.codeInspection.offlineViewer.OfflineProblemDescriptorNode;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.TreeTraversal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class InspectionTreeModel extends BaseTreeModel<InspectionTreeNode> {
  private static final Logger LOG = Logger.getInstance(InspectionTreeModel.class);
  private final InspectionRootNode myRoot = new InspectionRootNode(this);
  private final Map<InspectionTreeNode, Children> myChildren = new ConcurrentHashMap<>();
  private final Map<InspectionTreeNode, InspectionTreeNode> myParents = new ConcurrentHashMap<>();

  @Override
  public int getIndexOfChild(Object object, Object child) {
    return Collections.binarySearch(getChildren(object), (InspectionTreeNode)child, InspectionResultsViewComparator.INSTANCE);
  }

  public void reload() {
    treeNodesChanged(null, null, null);
  }

  private static class Children {
    private static final InspectionTreeNode[] EMPTY_ARRAY = new InspectionTreeNode[0];

    private volatile InspectionTreeNode[] myChildren = EMPTY_ARRAY;
    private final BidirectionalMap<Object, InspectionTreeNode> myUserObject2Node = new BidirectionalMap<>();
  }

  public InspectionTreeModel() {}

  @Override
  public List<? extends InspectionTreeNode> getChildren(Object parent) {
    Children nodes = myChildren.get(((InspectionTreeNode)parent));
    return nodes == null ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(nodes.myChildren));
  }

  @Override
  public InspectionRootNode getRoot() {
    return myRoot;
  }

  @Nullable
  public InspectionTreeNode getParent(InspectionTreeNode node) {
    return myParents.get(node);
  }

  public void traverse(InspectionTreeNode node, Processor<? super InspectionTreeNode> processor) {
    TreeTraversal.PRE_ORDER_DFS.traversal(node, n -> getChildren(n)).processEach(processor);
  }

  @NotNull
  public JBIterable<InspectionTreeNode> traverseFrom(InspectionTreeNode node, boolean direction) {
    return JBIterable.generate(node, n -> getParent(n)).filter(n -> getParent(n) != null).flatMap(n1 -> {
      InspectionTreeNode p = getParent(n1);
      @SuppressWarnings("ConstantConditions")
      List<? extends InspectionTreeNode> children = p.getChildren();
      int idx = getIndexOfChild(p, n1);
      InspectionTreeNode[] arr = children.toArray(InspectionTreeNode.EMPTY_ARRAY);
      List<? extends InspectionTreeNode> sublist = Arrays.asList(arr).subList(idx + ((n1 == node) ? 0 : 1), children.size());
      return TreeTraversal.PRE_ORDER_DFS.traversal(sublist, (InspectionTreeNode n) -> direction ? getChildren(n) : ContainerUtil.reverse(getChildren(n)));
    });
  }

  public void removeChild(@NotNull InspectionTreeNode node, int childIndex) {
    InspectionTreeNode removed = myChildren.get(node).myChildren[childIndex];
    remove(removed);
    treeNodesChanged(null, null, null);
    treeStructureChanged(null, null, null);
  }

  public void removeChildren(@NotNull InspectionTreeNode node) {
    doRemove(node, node);
    treeNodesChanged(null, null, null);
    treeStructureChanged(null, null, null);
  }

  public void remove(@NotNull InspectionTreeNode node) {
    doRemove(node, null);
    treeNodesChanged(null, null, null);
    treeStructureChanged(null, null, null);
  }

  private synchronized void doRemove(@NotNull InspectionTreeNode node, @Nullable InspectionTreeNode skip) {
    for (InspectionTreeNode child : getChildren(node)) {
      doRemove(child, skip);
    }
    if (node != skip) {
      InspectionTreeNode parent = myParents.remove(node);
      if (parent != null) {
        Children parentChildren = myChildren.get(parent);
        if (parentChildren != null) {
          parentChildren.myChildren = ArrayUtil.remove(parentChildren.myChildren, node);
          parentChildren.myUserObject2Node.removeValue(node);
        }
      }
    }
  }

  public synchronized void clearTree() {
    myChildren.clear();
    myParents.clear();
  }

  @NotNull
  public InspectionModuleNode createModuleNode(@NotNull Module module, @NotNull InspectionTreeNode parent) {
    return getOrAdd(module, () -> new InspectionModuleNode(module, this), parent);
  }

  @NotNull
  public InspectionPackageNode createPackageNode(String packageName, @NotNull InspectionTreeNode parent) {
    return getOrAdd(packageName, () -> new InspectionPackageNode(packageName, this), parent);
  }

  @NotNull
  public InspectionGroupNode createGroupNode(String group, @NotNull InspectionTreeNode parent) {
    return getOrAdd(group, () -> new InspectionGroupNode(group, this), parent);
  }

  @NotNull
  public InspectionSeverityGroupNode createSeverityGroupNode(SeverityRegistrar severityRegistrar, HighlightDisplayLevel level, @NotNull InspectionTreeNode parent) {
    return getOrAdd(level, () -> new InspectionSeverityGroupNode(severityRegistrar, level, this), parent);
  }

  @NotNull
  public RefElementNode createRefElementNode(@Nullable RefEntity entity,
                                             @NotNull Supplier<? extends RefElementNode> supplier,
                                             @NotNull InspectionTreeNode parent) {
    return getOrAdd(entity, () -> ReadAction.compute(supplier::get), parent);
  }

  public <T extends InspectionTreeNode> T createCustomNode(@NotNull Object userObject, @NotNull Supplier<T> supplier, @NotNull InspectionTreeNode parent) {
    return getOrAdd(userObject, supplier, parent);
  }

  @NotNull
  public InspectionNode createInspectionNode(@NotNull InspectionToolWrapper toolWrapper, InspectionProfileImpl profile, @NotNull InspectionTreeNode parent) {
    return getOrAdd(toolWrapper, () -> new InspectionNode(toolWrapper, profile, this), parent);
  }

  public void createProblemDescriptorNode(RefEntity element,
                                          @NotNull CommonProblemDescriptor descriptor,
                                          @NotNull InspectionToolPresentation presentation,
                                          @NotNull InspectionTreeNode parent) {
    getOrAdd(descriptor, () -> ReadAction.compute(() -> new ProblemDescriptionNode(element, descriptor, presentation, this)), parent);
  }

  public void createOfflineProblemDescriptorNode(@NotNull OfflineProblemDescriptor offlineDescriptor,
                                                 @NotNull OfflineDescriptorResolveResult resolveResult,
                                                 @NotNull InspectionToolPresentation presentation,
                                                 @NotNull InspectionTreeNode parent) {
    getOrAdd(offlineDescriptor,
             () -> ReadAction.compute(() -> new OfflineProblemDescriptorNode(resolveResult, presentation, offlineDescriptor, this)),
             parent);
  }

  private synchronized <T extends InspectionTreeNode> T getOrAdd(Object userObject, Supplier<T> supplier, InspectionTreeNode parent) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || !ApplicationManager.getApplication().isDispatchThread());
    Children children = myChildren.computeIfAbsent(parent, __ -> new Children());
    if (userObject == null) {
      userObject = ObjectUtils.NULL;
    }
    InspectionTreeNode node = children.myUserObject2Node.get(userObject);
    if (node == null) {
      node = supplier.get();
      InspectionTreeNode finalNode = node;
      int idx = ReadAction.compute(() -> Arrays.binarySearch(children.myChildren, finalNode, InspectionResultsViewComparator.INSTANCE));
      // it's allowed to have idx >= 0 for example for problem descriptor nodes.
      int insertionPoint = idx >= 0 ? idx : -idx - 1;
      children.myChildren = ArrayUtil.insert(children.myChildren, insertionPoint, node);
      myParents.put(node, parent);
      children.myUserObject2Node.put(userObject, node);

      LOG.assertTrue(children.myChildren.length == children.myUserObject2Node.size());

      if (node instanceof SuppressableInspectionTreeNode) {
        ((SuppressableInspectionTreeNode)node).nodeAdded();
      }

      TreePath path = TreePathUtil.pathToTreeNode(node);
      TreePath parentPath = path.getParentPath();
      treeNodesInserted(parentPath, null, null);
      while (parentPath != null) {
        treeStructureChanged(parentPath, null, null);
        parentPath = parentPath.getParentPath();
      }
    }
    //noinspection unchecked
    return (T)node;
  }
}
