// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.BatchModeDescriptorsUtil;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.intellij.codeInspection.CommonProblemDescriptor.DESCRIPTOR_COMPARATOR;

public class InspectionTree extends Tree {
  private static final Logger LOG = Logger.getInstance(InspectionTree.class);

  @NotNull private final GlobalInspectionContextImpl myContext;
  @NotNull private final ConcurrentMap<HighlightDisplayLevel, InspectionSeverityGroupNode> mySeverityGroupNodes = ContainerUtil.newConcurrentMap();
  @NotNull private final ConcurrentMap<HighlightDisplayLevel, ConcurrentMap<String[], InspectionGroupNode>> myGroups = ContainerUtil.newConcurrentMap();

  @NotNull private InspectionTreeState myState = new InspectionTreeState();
  private boolean myQueueUpdate;

  public InspectionTree(@NotNull GlobalInspectionContextImpl context,
                        @NotNull InspectionResultsView view) {
    Project project = context.getProject();
    setModel(new DefaultTreeModel(new InspectionRootNode(project, new InspectionTreeUpdater(view))));
    myContext = context;

    setCellRenderer(new InspectionTreeCellRenderer(view));
    setRootVisible(false);
    setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(this);
    addTreeWillExpandListener(new ExpandListener());

    myState.getExpandedUserObjects().add(project);

    TreeUtil.installActions(this);
    new TreeSpeedSearch(this, o -> InspectionsConfigTreeComparator.getDisplayTextToSort(o.getLastPathComponent().toString()));

    addTreeSelectionListener(e -> {
      TreePath newSelection = e.getNewLeadSelectionPath();
      if (newSelection != null && !isUnderQueueUpdate()) {
        myState.setSelectionPath(newSelection);
      }
    });
  }

  public void setQueueUpdate(boolean queueUpdate) {
    myQueueUpdate = queueUpdate;
  }

  public boolean isUnderQueueUpdate() {
    return myQueueUpdate;
  }

  public void removeAllNodes() {
    mySeverityGroupNodes.clear();
    myGroups.clear();
    getRoot().removeAllChildren();
    ApplicationManager.getApplication().invokeLater(() -> {
      InspectionResultsView view = myContext.getView();
      if (view != null && !view.isDisposed()) {
        nodeStructureChanged(getRoot());
      }
    });
  }

  public InspectionTreeNode getRoot() {
    return (InspectionTreeNode)getModel().getRoot();
  }

  @Nullable
  public String[] getSelectedGroupPath() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    final TreePath commonPath = TreeUtil.findCommonPath(paths);
    for (Object n : commonPath.getPath()) {
      if (n instanceof InspectionGroupNode) {
        return getGroupPath((InspectionGroupNode)n);
      }
    }
    return null;
  }

  @Nullable
  public InspectionToolWrapper getSelectedToolWrapper(boolean allowDummy) {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    InspectionToolWrapper toolWrapper = null;
    for (TreePath path : paths) {
      Object[] nodes = path.getPath();
      for (int j = nodes.length - 1; j >= 0; j--) {
        Object node = nodes[j];
        if (node instanceof InspectionGroupNode) {
          return null;
        }
        if (node instanceof InspectionNode) {
          InspectionToolWrapper wrapper = ((InspectionNode)node).getToolWrapper();
          if (!allowDummy && getContext().getPresentation(wrapper).isDummy()) {
            continue;
          }
          if (toolWrapper == null) {
            toolWrapper = wrapper;
          }
          else if (toolWrapper != wrapper) {
            return null;
          }
          break;
        }
      }
    }

    return toolWrapper;
  }

  @Override
  public String getToolTipText(MouseEvent e) {
    TreePath path = getPathForLocation(e.getX(), e.getY());
    if (path == null) return null;
    Object lastComponent = path.getLastPathComponent();
    if (!(lastComponent instanceof ProblemDescriptionNode)) return null;
    return ((ProblemDescriptionNode)lastComponent).getToolTipText();
  }

  @Nullable
  public RefEntity getCommonSelectedElement() {
    final Object node = getCommonSelectedNode();
    return node instanceof RefElementNode ? ((RefElementNode)node).getElement() : null;
  }

  @Nullable
  private Object getCommonSelectedNode() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    final Object[][] resolvedPaths = new Object[paths.length][];
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      resolvedPaths[i] = path.getPath();
    }

    Object currentCommonNode = null;
    for (int i = 0; i < resolvedPaths[0].length; i++) {
      final Object currentNode = resolvedPaths[0][i];
      for (int j = 1; j < resolvedPaths.length; j++) {
        final Object o = resolvedPaths[j][i];
        if (!o.equals(currentNode)) {
          return currentCommonNode;
        }
      }
      currentCommonNode = currentNode;
    }
    return currentCommonNode;
  }

  @NotNull
  public RefEntity[] getSelectedElements() {
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths != null) {
      InspectionToolWrapper toolWrapper = getSelectedToolWrapper(true);
      if (toolWrapper == null) return RefEntity.EMPTY_ELEMENTS_ARRAY;

      Set<RefEntity> result = new LinkedHashSet<>();
      for (TreePath selectionPath : selectionPaths) {
        final InspectionTreeNode node = (InspectionTreeNode)selectionPath.getLastPathComponent();
        addElementsInNode(node, result);
      }
      return ArrayUtil.reverseArray(result.toArray(RefEntity.EMPTY_ELEMENTS_ARRAY));
    }
    return RefEntity.EMPTY_ELEMENTS_ARRAY;
  }

  private static void addElementsInNode(InspectionTreeNode node, Set<RefEntity> out) {
    if (!node.isValid()) return;
    if (node instanceof RefElementNode) {
      final RefEntity element = ((RefElementNode)node).getElement();
      out.add(element);
    }
    if (node instanceof ProblemDescriptionNode) {
      final RefEntity element = ((ProblemDescriptionNode)node).getElement();
      out.add(element);
    }
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
      addElementsInNode(child, out);
    }
  }

  @NotNull
  public CommonProblemDescriptor[] getAllValidSelectedDescriptors() {
    return BatchModeDescriptorsUtil.flattenDescriptors(getSelectedDescriptorPacks(false, null, true));
  }

  @NotNull
  public CommonProblemDescriptor[] getSelectedDescriptors() {
    return BatchModeDescriptorsUtil.flattenDescriptors(getSelectedDescriptorPacks(false, null, false));
  }

  @NotNull
  public List<CommonProblemDescriptor[]> getSelectedDescriptorPacks(boolean sortedByPosition,
                                                                    @Nullable Set<VirtualFile> readOnlyFilesSink,
                                                                    boolean allowResolved) {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return Collections.emptyList();

    // key can be node or VirtualFile (if problem descriptor node parent is a file/member RefElementNode).
    MultiMap<Object, CommonProblemDescriptor> parentToChildNode = new MultiMap<>();
    TreeUtil
      .treePathTraverser(this)
      .withRoots(Arrays.asList(paths))
      .traverse()
      .map(p -> p.getLastPathComponent())
      .filter(ProblemDescriptionNode.class)
      .filter(node -> node.getDescriptor() != null && isNodeValidAndIncluded(node, allowResolved))
      .consumeEach(node -> {
        Object key = getVirtualFileOrEntity(node.getElement());
        parentToChildNode.putValue(key, node.getDescriptor());
      });


    final List<CommonProblemDescriptor[]> descriptors = new ArrayList<>();
    for (Map.Entry<Object, Collection<CommonProblemDescriptor>> entry : parentToChildNode.entrySet()) {
      Object key = entry.getKey();
      if (readOnlyFilesSink != null && key instanceof VirtualFile && !((VirtualFile)key).isWritable()) {
        readOnlyFilesSink.add((VirtualFile)key);
      }
      Stream<CommonProblemDescriptor> stream = entry.getValue().stream();
      if (sortedByPosition) {
        stream = stream.sorted(DESCRIPTOR_COMPARATOR);
      }
      descriptors.add(stream.toArray(CommonProblemDescriptor.ARRAY_FACTORY::create));
    }

    return descriptors;
  }

  @NotNull
  InspectionTreeNode getToolParentNode(@NotNull InspectionToolWrapper toolWrapper,
                                       HighlightDisplayLevel errorLevel,
                                       boolean groupedBySeverity,
                                       boolean isSingleInspectionRun) {
    //synchronize
    if (!groupedBySeverity && isSingleInspectionRun) {
      return getRoot();
    }
    String[] groupPath = toolWrapper.getGroupPath();
    if (groupPath.length == 0) {
      LOG.error("groupPath is empty for tool: " + toolWrapper.getShortName() + ", class: " + toolWrapper.getTool().getClass());
      return getRelativeRootNode(groupedBySeverity, errorLevel);
    }
    ConcurrentMap<String[], InspectionGroupNode> map = myGroups.get(errorLevel);
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myGroups, errorLevel, ConcurrentCollectionFactory.createMap(new TObjectHashingStrategy<String[]>() {
        @Override
        public int computeHashCode(String[] object) {
          return Arrays.hashCode(object);
        }

        @Override
        public boolean equals(String[] o1, String[] o2) {
          return Arrays.equals(o1, o2);
        }
      }));
    }
    InspectionGroupNode group;
    if (groupedBySeverity) {
      group = map.get(groupPath);
    }
    else {
      group = null;
      for (Map<String[], InspectionGroupNode> groupMap : myGroups.values()) {
        if ((group = groupMap.get(groupPath)) != null) break;
      }
    }
    if (group == null) {
      if (isSingleInspectionRun) {
        return getRelativeRootNode(true, errorLevel);
      }
      group = map.computeIfAbsent(groupPath, p -> insertGroupNode(p, getRelativeRootNode(groupedBySeverity, errorLevel)));
    }
    return group;
  }

  @NotNull
  private InspectionTreeNode getRelativeRootNode(boolean isGroupedBySeverity, HighlightDisplayLevel level) {
    if (isGroupedBySeverity) {
      InspectionSeverityGroupNode severityGroupNode = mySeverityGroupNodes.get(level);
      if (severityGroupNode == null) {
        InspectionSeverityGroupNode newNode = new InspectionSeverityGroupNode(myContext.getCurrentProfile().getProfileManager().getSeverityRegistrar(), level);
        severityGroupNode = ConcurrencyUtil.cacheOrGet(mySeverityGroupNodes, level, newNode);
        if (severityGroupNode == newNode) {
          InspectionTreeNode root = getRoot();
          root.insertByOrder(severityGroupNode, false);
        }
      }
      return severityGroupNode;
    }
    return getRoot();
  }

  public boolean areDescriptorNodesSelected() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return false;
    for (TreePath path : paths) {
      if (!(path.getLastPathComponent() instanceof ProblemDescriptionNode)) {
        return false;
      }
    }
    return true;
  }

  public int getSelectedProblemCount(boolean allowSuppressed) {
    int count = 0;
    for (TreePath path : TreeUtil.selectMaximals(getSelectionPaths())) {
      count += ((InspectionTreeNode)path.getLastPathComponent()).getProblemCount(allowSuppressed);
    }
    return count;
  }

  private static boolean isNodeValidAndIncluded(ProblemDescriptionNode node, boolean allowResolved) {
    return node.isValid() && (allowResolved ||
                              (!node.isExcluded() &&
                               !node.isAlreadySuppressedFromView() &&
                               !node.isQuickFixAppliedFromView()));
  }

  private void nodeStructureChanged(InspectionTreeNode node) {
    ((DefaultTreeModel)getModel()).nodeStructureChanged(node);
  }

  public void queueUpdate() {
    ((InspectionRootNode) getRoot()).getUpdater().update(true);
  }

  public void restoreExpansionAndSelection(boolean treeNodesMightChange) {
    myState.restoreExpansionAndSelection(this, treeNodesMightChange);
  }

  public void removeSelectedProblems() {
    if (!getContext().getUIOptions().FILTER_RESOLVED_ITEMS) {
      return;
    }
    synchronized (getContext().getView().getTreeStructureUpdateLock()) {
      TreePath[] selected = getSelectionPaths();
      if (selected == null) return;
      Set<InspectionTreeNode> processedNodes = new THashSet<>();
      List<InspectionTreeNode> toRemove = new ArrayList<>();
      List<TreePath> pathsToSelect = new ArrayList<>();
      for (TreePath path : selected) {
        Object[] nodePath = path.getPath();

        // ignore root
        for (int i = 1; i < nodePath.length; i++) {
          InspectionTreeNode node = (InspectionTreeNode) nodePath[i];
          if (!processedNodes.add(node)) break;

          if (shouldDelete(node)) {
            toRemove.add(node);
            TreePath toSelect = getParentPath(path, nodePath.length - i);
            if (toSelect != null) {
              pathsToSelect.add(toSelect);
            }
            break;
          }
        }
      }

      if (toRemove.isEmpty()) return;
      DefaultTreeModel model = (DefaultTreeModel)getModel();
      for (InspectionTreeNode node : toRemove) {
        TreeNode parent = node.getParent();
        if (parent != null) {
          model.removeNodeFromParent(node);
        }
      }

      TreeUtil.selectPath(this, TreeUtil.findCommonPath(pathsToSelect.toArray(new TreePath[0])));
    }
    revalidate();
    repaint();
  }

  private static TreePath getParentPath(TreePath path, int ord) {
    TreePath parent = path;
    for (int j = 0; j < ord; j++) {
      parent = parent.getParentPath();
    }
    return parent;
  }

  private boolean shouldDelete(InspectionTreeNode node) {
    if (node instanceof RefElementNode) {
      RefElementNode refElementNode = (RefElementNode)node;
      RefEntity refEntity = refElementNode.getElement();
      if (refEntity == null || isEntityExcludedOrResolvedRecursively(refEntity, refElementNode.getPresentation())) {
        return true;
      }
    }
    else if (node instanceof ProblemDescriptionNode) {
      ProblemDescriptionNode problemDescriptionNode = (ProblemDescriptionNode)node;
      CommonProblemDescriptor descriptor = problemDescriptionNode.getDescriptor();
      InspectionToolPresentation presentation = problemDescriptionNode.getPresentation();
      if (descriptor == null || presentation.isExcluded(descriptor) || presentation.isProblemResolved(descriptor)) {
        return true;
      }
    }
    else if (node instanceof InspectionGroupNode || node instanceof InspectionSeverityGroupNode || node instanceof InspectionModuleNode || node instanceof InspectionPackageNode) {
      return IntStream.range(0, node.getChildCount()).mapToObj(i -> (InspectionTreeNode)node.getChildAt(i)).allMatch(this::shouldDelete);
    }
    else if (node instanceof InspectionNode) {
      InspectionToolPresentation presentation = myContext.getPresentation(((InspectionNode)node).getToolWrapper());
      SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> problemElements = presentation.getProblemElements();
      if (problemElements.isEmpty()) {
        return true;
      }
      return problemElements.keys().stream().allMatch(entity -> presentation.isExcluded(entity));
    }
    return false;
  }

  private static boolean isEntityExcludedOrResolvedRecursively(RefEntity key, InspectionToolPresentation presentation) {
    if (presentation.isProblemResolved(key) ||
        presentation.isExcluded(key) ||
        presentation.isSuppressed(key)) {
      return true;
    }
    List<RefEntity> children = key.getChildren();
    return !children.isEmpty() && children.stream().allMatch(entity -> isEntityExcludedOrResolvedRecursively(entity, presentation));
  }

  public InspectionTreeState getTreeState() {
    return myState;
  }

  public void setTreeState(@NotNull InspectionTreeState treeState) {
    myState = treeState;
  }

  private class ExpandListener implements TreeWillExpandListener {
    @Override
    public void treeWillExpand(TreeExpansionEvent event) {
      final InspectionTreeNode node = (InspectionTreeNode)event.getPath().getLastPathComponent();
      myState.getExpandedUserObjects().add(node.getUserObject());
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent event) {
      InspectionTreeNode node = (InspectionTreeNode)event.getPath().getLastPathComponent();
      myState.getExpandedUserObjects().remove(node.getUserObject());
    }
  }

  @NotNull
  public GlobalInspectionContextImpl getContext() {
    return myContext;
  }

  private InspectionGroupNode insertGroupNode(@NotNull String[] groupPath, InspectionTreeNode parent) {
    InspectionTreeNode currentNode = parent;

    for (int groupIdx = 0; groupIdx < groupPath.length; groupIdx++) {
      String subGroup = groupPath[groupIdx];

      InspectionTreeNode next = null;
      for (int i = 0; i < currentNode.getChildCount(); i++) {
        TreeNode child = currentNode.getChildAt(i);
        if (child instanceof InspectionGroupNode && ((InspectionGroupNode)child).getSubGroup().equals(subGroup)) {
          next = (InspectionTreeNode)child;
          break;
        }
      }

      if (next == null) {
        for (int i = groupIdx; i < groupPath.length; i++) {
          InspectionResultsView view = getContext().getView();
          if (view != null && !view.isDisposed()) {
            currentNode = currentNode.insertByOrder(new InspectionGroupNode(groupPath[i]), false);
          }
        }
        break;
      }
      else {
        currentNode = next;
      }
    }

    return (InspectionGroupNode)currentNode;
  }

  @NotNull
  private static String[] getGroupPath(@NotNull InspectionGroupNode node) {
    List<String> path = new ArrayList<>(2);
    while (true) {
      TreeNode parent = node.getParent();
      if (!(parent instanceof InspectionGroupNode)) break;
      node = (InspectionGroupNode)parent;
      path.add(node.getSubGroup());
    }
    return ArrayUtil.toStringArray(path);
  }

  @Nullable
  private static Object getVirtualFileOrEntity(@Nullable RefEntity entity) {
    if (entity instanceof RefElement) {
      SmartPsiElementPointer pointer = ((RefElement)entity).getPointer();
      if (pointer != null) {
        VirtualFile file = pointer.getVirtualFile();
        if (file != null) {
          return file;
        }
      }
    }
    return entity;
  }
}
