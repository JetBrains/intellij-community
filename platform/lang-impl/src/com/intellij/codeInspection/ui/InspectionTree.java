// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionToolResultExporter;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.BatchModeDescriptorsUtil;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefElementImpl;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.*;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreeCollector.TreePathRoots;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.intellij.codeInspection.CommonProblemDescriptor.DESCRIPTOR_COMPARATOR;

public final class InspectionTree extends Tree {
  private static final Logger LOG = Logger.getInstance(InspectionTree.class);

  private final InspectionTreeModel myModel;

  private final OccurenceNavigator myOccurenceNavigator = new MyOccurrenceNavigator();
  private final InspectionResultsView myView;
  private final Map<ProblemDescriptionNode, CancellablePromise<String>> scheduledTooltipTasks = new ConcurrentHashMap<>();

  public InspectionTree(@NotNull InspectionResultsView view) {
    myView = view;
    Disposer.register(myView, () -> {
      scheduledTooltipTasks.forEach((node, promise) -> promise.cancel());
      scheduledTooltipTasks.clear();
    });

    myModel = new InspectionTreeModel();
    Disposer.register(view, myModel);
    setModel(new AsyncTreeModel(myModel, false, view));

    setCellRenderer(new InspectionTreeCellRenderer(view));
    setRootVisible(true);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getSelectionModel().addTreeSelectionListener(e -> {
        if (!myView.isDisposed()) {
          myView.syncRightPanel();
          if (myView.isAutoScrollMode()) {
            OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), false);
          }
        }
      });

      EditSourceOnDoubleClickHandler.install(this);
      EditSourceOnEnterKeyHandler.install(this);
      TreeUtil.installActions(this);
      PopupHandler.installPopupMenu(this, IdeActions.INSPECTION_TOOL_WINDOW_TREE_POPUP, ActionPlaces.CODE_INSPECTION);
      TreeSpeedSearch.installOn(this, false, o -> InspectionsConfigTreeComparator.getDisplayTextToSort(o.getLastPathComponent().toString()));
    }

    getModel().addTreeModelListener(new TreeModelAdapter() {
      //TODO the same as DiscoveredTestTree (see setRootVisible)
      boolean myAlreadyDone;
      @Override
      protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
        if (!myAlreadyDone && getSelectionCount() == 0) {
          myAlreadyDone = true;
          EdtInvocationManager.getInstance().invokeLater(() -> {
            expandPath(new TreePath(myModel.getRoot()));
            SmartExpander.installOn(InspectionTree.this);
            if (!myView.isDisposed()) {
              myView.syncRightPanel();
            }
          });
        }
      }
    });
  }

  public InspectionTreeNode getRoot() {
    return myModel.getRoot();
  }

  public InspectionTreeModel getInspectionTreeModel() {
    return myModel;
  }

  void removeAllNodes() {
    myModel.clearTree();
  }

  public String @Nullable [] getSelectedGroupPath() {
    TreePath commonPath = TreePathUtil.findCommonAncestor(getSelectionPaths());
    if (commonPath == null) return null;
    for (Object n : commonPath.getPath()) {
      if (n instanceof InspectionGroupNode) {
        return getGroupPath((InspectionGroupNode)n);
      }
    }
    return null;
  }

  public @Nullable InspectionToolWrapper<?,?> getSelectedToolWrapper(boolean allowDummy) {
    return getSelectedToolWrapper(allowDummy, getSelectionPaths());
  }

  @Nullable InspectionToolWrapper<?,?> getSelectedToolWrapper(boolean allowDummy, TreePath @Nullable [] paths) {
    InspectionProfileImpl profile = myView.getCurrentProfile();
    if (profile == null) return null;
    String singleToolName = profile.getSingleTool();
    if (paths == null) {
      if (singleToolName != null) {
        InspectionToolWrapper<?,?> tool = profile.getInspectionTool(singleToolName, myView.getProject());
        LOG.assertTrue(tool != null);
        return tool;
      }
      return null;
    }
    InspectionToolWrapper<?,?> resultWrapper = null;
    for (TreePath path : paths) {
      Object[] nodes = path.getPath();
      for (int j = nodes.length - 1; j >= 0; j--) {
        Object node = nodes[j];
        if (node instanceof InspectionGroupNode) {
          return null;
        }
        InspectionToolWrapper<?,?> wrapper = null;
        if (node instanceof InspectionNode) {
          wrapper = ((InspectionNode)node).getToolWrapper();
        } else if (node instanceof SuppressableInspectionTreeNode) {
          wrapper = ((SuppressableInspectionTreeNode)node).getPresentation().getToolWrapper();
        }
        if (wrapper == null || !allowDummy && getContext().getPresentation(wrapper).isDummy()) {
          continue;
        }
        if (resultWrapper == null) {
          resultWrapper = wrapper;
        }
        else if (resultWrapper != wrapper) {
          return null;
        }
        break;
      }
    }

    if (resultWrapper == null && singleToolName != null) {
      InspectionToolWrapper<?,?> tool = profile.getInspectionTool(singleToolName, myView.getProject());
      LOG.assertTrue(tool != null);
      return tool;
    }

    return resultWrapper;
  }

  public static @Nullable InspectionToolWrapper<?, ?> findWrapper(Object[] selectedNode) {
    InspectionToolWrapper<?, ?> resultWrapper = null;
    for (Object node : selectedNode) {
      if (node instanceof InspectionGroupNode) {
        return null;
      }

      InspectionToolWrapper<?,?> wrapper = null;
      if (node instanceof InspectionNode) {
        wrapper = ((InspectionNode)node).getToolWrapper();
      }
      else if (node instanceof SuppressableInspectionTreeNode) {
        wrapper = ((SuppressableInspectionTreeNode)node).getPresentation().getToolWrapper();
      }
      if (wrapper != null) {
        if (resultWrapper == null) {
          resultWrapper = wrapper;
        }
        else if (resultWrapper != wrapper) {
          return null;
        }
      }
    }
    return resultWrapper;
  }

  private static @Nullable InspectionToolWrapper<?,?> getToolWrapper(InspectionTreeNode node) {
    while (node != null) {
      if (node instanceof InspectionNode) {
        return ((InspectionNode)node).getToolWrapper();
      }
      else if (node instanceof SuppressableInspectionTreeNode) {
        return ((SuppressableInspectionTreeNode)node).getPresentation().getToolWrapper();
      }
      node = node.getParent();
    }
    return null;
  }

  private @Nullable InspectionToolWrapper<?, ?> getSingleToolWrapper() {
    InspectionProfileImpl profile = myView.getCurrentProfile();
    if (profile == null) return null;
    String singleToolName = profile.getSingleTool();
    if (singleToolName == null) return null;
    InspectionToolWrapper<?,?> tool = profile.getInspectionTool(singleToolName, myView.getProject());
    LOG.assertTrue(tool != null);
    return tool;
  }

  @Override
  public String getToolTipText(MouseEvent e) {
    TreePath path = getPathForLocation(e.getX(), e.getY());
    if (path == null) return null;
    Object lastComponent = path.getLastPathComponent();
    if (!(lastComponent instanceof ProblemDescriptionNode node)) return null;

    if (!node.needCalculateTooltip()) return node.getToolTipText();

    Promise<@NlsContexts.Tooltip String> tooltipLazy = scheduledTooltipTasks.computeIfAbsent(node, key -> {
      final var tooltipManager = IdeTooltipManager.getInstance();
      final Component component = e.getComponent();

      return ReadAction.nonBlocking(() -> node.getToolTipText())
        .finishOnUiThread(ModalityState.any(), tooltipText -> tooltipManager.updateShownTooltip(component))
        .submit(AppExecutorUtil.getAppExecutorService())
        .onError(throwable -> {
          if (!(throwable instanceof CancellationException)) {
            LOG.error("Exception in ProblemDescriptionNode#getToolTipText", throwable);
          }
          scheduledTooltipTasks.remove(node);
        })
        .onSuccess(tooltipText -> scheduledTooltipTasks.remove(node));
    });

    if (tooltipLazy.isSucceeded()) {
      try {
        return tooltipLazy.blockingGet(0);
      }
      catch (TimeoutException | ExecutionException error) {
        LOG.error(error);
      }
    }
    return UIBundle.message("crumbs.calculating.tooltip");
  }

  @Nullable RefEntity getCommonSelectedElement() {
    final TreePath[] paths = getSelectionPaths();
    final TreePath ancestor = TreePathUtil.findCommonAncestor(paths);
    if (ancestor == null) return null;
    final Object node = ancestor.getLastPathComponent();
    return node instanceof RefElementNode ? ((RefElementNode)node).getElement() : null;
  }

  public RefEntity @NotNull [] getSelectedElements() {
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths == null) return RefEntity.EMPTY_ELEMENTS_ARRAY;
    return getElementsFromSelection(selectionPaths);
  }

  RefEntity @NotNull [] getElementsFromSelection(TreePath @NotNull [] selectionPaths) {
    InspectionToolWrapper<?,?> toolWrapper = getSelectedToolWrapper(true, selectionPaths);
    if (toolWrapper == null) return RefEntity.EMPTY_ELEMENTS_ARRAY;
    Set<RefEntity> result = new LinkedHashSet<>();
    for (TreePath selectionPath : selectionPaths) {
      final InspectionTreeNode node = (InspectionTreeNode)selectionPath.getLastPathComponent();
      addElementsInNode(node, result);
    }
    return ArrayUtil.reverseArray(result.toArray(RefEntity.EMPTY_ELEMENTS_ARRAY));
  }

  @NotNull
  OccurenceNavigator getOccurenceNavigator() {
    return myOccurenceNavigator;
  }

  public void selectNode(@NotNull InspectionTreeNode node) {
    TreePath path = TreePathUtil.pathToTreeNode(node);
    if (path != null) TreeUtil.promiseSelect(this, path);
  }

  private static void addElementsInNode(@NotNull InspectionTreeNode node, @NotNull Set<? super RefEntity> out) {
    if (!node.isValid()) return;
    if (node instanceof RefElementNode) {
      final RefEntity element = ((RefElementNode)node).getElement();
      out.add(element);
    }
    if (node instanceof ProblemDescriptionNode) {
      final RefEntity element = ((ProblemDescriptionNode)node).getElement();
      out.add(element);
    }

    for (InspectionTreeNode child : node.getChildren()) {
      addElementsInNode(child, out);
    }
  }

  CommonProblemDescriptor @NotNull [] getAllValidSelectedDescriptors() {
    return BatchModeDescriptorsUtil.flattenDescriptors(getSelectedDescriptorPacks(false, null, true, null));
  }

  CommonProblemDescriptor @NotNull [] getSelectedDescriptors() {
    return BatchModeDescriptorsUtil.flattenDescriptors(getSelectedDescriptorPacks(false, null, false, null));
  }

  public @NotNull List<CommonProblemDescriptor[]> getSelectedDescriptorPacks(boolean sortedByPosition,
                                                                             @Nullable Set<? super VirtualFile> readOnlyFilesSink,
                                                                             boolean allowResolved,
                                                                             TreePath[] paths) {
    if (paths == null) {
      ThreadingAssertions.assertEventDispatchThread();
      paths = getSelectionPaths();
    }
    if (paths == null) return Collections.emptyList();
    // key can be node or VirtualFile (if problem descriptor node parent is a file/member RefElementNode).
    //TODO expected thread
    List<InspectionTreeNode> nodes = ContainerUtil.map(paths, p -> (InspectionTreeNode)p.getLastPathComponent());
    return getSelectedDescriptors(sortedByPosition, readOnlyFilesSink, allowResolved, nodes);
  }

   public CommonProblemDescriptor @NotNull [] getSelectedDescriptors(AnActionEvent e) {
     Object[] selectedNodes = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
     if (selectedNodes == null) {
       return CommonProblemDescriptor.EMPTY_ARRAY;
     }
     List<CommonProblemDescriptor[]> descriptors = getSelectedDescriptors(false, null, false, ContainerUtil.map(selectedNodes, o -> (InspectionTreeNode)o));
     return BatchModeDescriptorsUtil.flattenDescriptors(descriptors);
  }
  
  private @NotNull List<CommonProblemDescriptor[]> getSelectedDescriptors(boolean sortedByPosition,
                                                                          @Nullable Set<? super VirtualFile> readOnlyFilesSink,
                                                                          boolean allowResolved,
                                                                          @NotNull List<? extends InspectionTreeNode> nodes) {
    MultiMap<Object, CommonProblemDescriptor> parentToChildNode = new MultiMap<>();
    TreeTraversal.PLAIN_BFS.traversal(
        nodes,
      (InspectionTreeNode n) -> myModel.getChildren(n))
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
      descriptors.add(stream.distinct().toArray(CommonProblemDescriptor.ARRAY_FACTORY::create));
    }

    return descriptors;
  }

  @Override
  public TreePath @Nullable [] getSelectionPaths() {
    ThreadingAssertions.assertEventDispatchThread();
    return super.getSelectionPaths();
  }

  @NotNull
  InspectionTreeNode getToolProblemsRootNode(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                                             @NotNull HighlightDisplayLevel errorLevel,
                                             boolean groupedBySeverity,
                                             boolean isSingleInspectionRun) {
    InspectionTreeNode parent = getToolParentNode(toolWrapper, errorLevel, groupedBySeverity, isSingleInspectionRun);
    if (isSingleInspectionRun) {
      return parent;
    }
    return myModel.createInspectionNode(toolWrapper, myView.getCurrentProfile(), parent);
  }

  private @NotNull InspectionTreeNode getToolParentNode(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                                                        @NotNull HighlightDisplayLevel errorLevel,
                                                        boolean groupedBySeverity,
                                                        boolean isSingleInspectionRun) {
    //synchronize
    if (!groupedBySeverity && isSingleInspectionRun) {
      return myModel.getRoot();
    }

    InspectionTreeNode currentNode = groupedBySeverity
                                     ? myModel.createSeverityGroupNode(myView.getCurrentProfile().getProfileManager().getSeverityRegistrar(),
                                                                       errorLevel,
                                                                       myModel.getRoot())
                                     : myModel.getRoot();

    if (isSingleInspectionRun) return currentNode;

    String[] groupPath = toolWrapper.getGroupPath();
    if (groupPath.length == 0) {
      LOG.error("groupPath is empty for tool: " + toolWrapper.getShortName() + ", class: " + toolWrapper.getTool().getClass());
    }

    for (@Nls String subGroup : groupPath) {
      currentNode = myModel.createGroupNode(subGroup, currentNode);
    }

    return currentNode;
  }

  boolean areDescriptorNodesSelected() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return false;
    for (TreePath path : paths) {
      if (!(path.getLastPathComponent() instanceof ProblemDescriptionNode)) {
        return false;
      }
    }
    return true;
  }

  int getSelectedProblemCount() {
    int count = 0;
    for (TreePath path : TreePathRoots.collect(getSelectionPaths())) {
      LevelAndCount[] levels = ((InspectionTreeNode)path.getLastPathComponent()).getProblemLevels();
      for (LevelAndCount level : levels) {
        count += level.getCount();
      }
    }
    return count;
  }

  private static boolean isNodeValidAndIncluded(ProblemDescriptionNode node, boolean allowResolved) {
    return node.isValid() && (allowResolved ||
                              (!node.isExcluded() &&
                               !node.isAlreadySuppressedFromView() &&
                               !node.isQuickFixAppliedFromView()));
  }

  public void removeSelectedProblems() {
    ThreadingAssertions.assertEventDispatchThread();
    if (!getContext().getUIOptions().FILTER_RESOLVED_ITEMS) return;
    TreePath[] selected = getSelectionPaths();
    if (selected == null) return;
    Set<InspectionTreeNode> processedNodes = new HashSet<>();
    List<InspectionTreeNode> toRemove = new ArrayList<>();
    for (TreePath path : selected) {
      Object[] nodePath = path.getPath();

      // ignore root
      for (int i = 1; i < nodePath.length; i++) {
        InspectionTreeNode node = (InspectionTreeNode) nodePath[i];
        if (!processedNodes.add(node)) continue;

        if (shouldDelete(node)) {
          toRemove.add(node);
          break;
        }
      }
    }

    if (toRemove.isEmpty()) return;

    TreePath pathToSelect = null;
    if (selected.length == 1) {
      final InspectionTreeNode nextNode = myModel
        .traverseFrom((InspectionTreeNode) selected[0].getLastPathComponent(), true)
        .filter(n -> !shouldDelete(n)).first();
      if (nextNode != null) pathToSelect = TreeUtil.getPathFromRoot(nextNode);
    } else {
      TreePath commonAliveAncestorPath = TreePathUtil.findCommonAncestor(selected);
      while (commonAliveAncestorPath != null && shouldDelete((InspectionTreeNode) commonAliveAncestorPath.getLastPathComponent())) {
        commonAliveAncestorPath = commonAliveAncestorPath.getParentPath();
      }
      if (commonAliveAncestorPath != null) pathToSelect = commonAliveAncestorPath;
    }

    for (InspectionTreeNode node : toRemove) {
      InspectionTreeNode parent = node.getParent();
      if (parent != null) {
        myModel.remove(node);
      }
    }

    TreeUtil.selectPath(this, pathToSelect);

    revalidate();
    repaint();
  }

  private boolean shouldDelete(InspectionTreeNode node) {
    if (node instanceof RefElementNode refElementNode) {
      InspectionToolPresentation presentation = refElementNode.getPresentation();
      RefEntity element = refElementNode.getElement();
      if (element == null ||
          presentation.isProblemResolved(element) ||
          presentation.isExcluded(element) ||
          presentation.isSuppressed(element)) {
        return true;
      }
      List<? extends InspectionTreeNode> children = node.getChildren();
      return !children.isEmpty() && ContainerUtil.and(children, this::shouldDelete);
    }
    if (node instanceof ProblemDescriptionNode problemDescriptionNode) {
      CommonProblemDescriptor descriptor = problemDescriptionNode.getDescriptor();
      InspectionToolPresentation presentation = problemDescriptionNode.getPresentation();
      return descriptor == null || presentation.isExcluded(descriptor) || presentation.isProblemResolved(descriptor);
    }
    if (node instanceof InspectionGroupNode || node instanceof InspectionSeverityGroupNode || node instanceof InspectionModuleNode || node instanceof InspectionPackageNode) {
      return ContainerUtil.and(node.getChildren(), this::shouldDelete);
    }
    if (node instanceof InspectionNode) {
      InspectionToolResultExporter presentation = myView.getGlobalInspectionContext().getPresentation(((InspectionNode)node).getToolWrapper());
      SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> problemElements = presentation.getProblemElements();
      if (problemElements.isEmpty()) {
        return true;
      }
      return ContainerUtil.and(problemElements.keys(), entity -> presentation.isExcluded(entity));
    }
    return false;
  }

  public @NotNull GlobalInspectionContextImpl getContext() {
    return myView.getGlobalInspectionContext();
  }

  private static String @NotNull [] getGroupPath(@NotNull InspectionGroupNode node) {
    List<String> path = new ArrayList<>(2);
    while (true) {
      InspectionTreeNode parent = node.getParent();
      if (!(parent instanceof InspectionGroupNode)) break;
      node = (InspectionGroupNode)parent;
      path.add(node.getSubGroup());
    }
    return ArrayUtilRt.toStringArray(path);
  }

  private static @Nullable Object getVirtualFileOrEntity(@Nullable RefEntity entity) {
    if (entity instanceof RefElement) {
      SmartPsiElementPointer<?> pointer = ((RefElement)entity).getPointer();
      if (pointer != null) {
        VirtualFile file = pointer.getVirtualFile();
        if (file != null) {
          return file;
        }
      }
    }
    return entity;
  }

  public static @Nullable PsiElement getSelectedElement(@NotNull AnActionEvent e) {
    PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
    if (element != null) {
      return element;
    }
    RefEntity[] entities = getSelectedRefElements(e);
    RefEntity refEntity = ContainerUtil.find(entities, entity -> entity instanceof RefElement);
    return refEntity != null ? ((RefElement)refEntity).getPsiElement() : null;
  }

  public static RefEntity @NotNull [] getSelectedRefElements(@NotNull AnActionEvent e) {
    Object[] nodes = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (nodes != null) {
      HashSet<RefEntity> entities = new HashSet<>();
      for (Object node : nodes) {
        addElementsInNode((InspectionTreeNode)node, entities);
      }
      return entities.toArray(entities.toArray(RefEntity.EMPTY_ELEMENTS_ARRAY));
    }
    return RefEntity.EMPTY_ELEMENTS_ARRAY;
  }

  private final class MyOccurrenceNavigator implements OccurenceNavigator {
    @Override
    public boolean hasNextOccurence() {
      return getNextOccurrence(true) != null;
    }

    @Override
    public boolean hasPreviousOccurence() {
      return getNextOccurrence(false) != null;
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      return goNextOccurrence(true);
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
      return goNextOccurrence(false);
    }

    private @Nullable OccurenceInfo goNextOccurrence(boolean next) {
      InspectionTreeNode node = getNextOccurrence(next);
      if (node == null) return null;
      selectNode(node);
      return Registry.is("ide.usages.next.previous.occurrence.only.show.in.preview") && InspectionTree.this.isShowing()
             ? null
             : new OccurenceInfo(createDescriptorForNode(node), -1, -1);
    }

    private @Nullable InspectionTreeNode getNextOccurrence(boolean next) {
      InspectionTreeNode selected = ObjectUtils.notNull(getSelectedNode(), getRoot());
      InspectionTreeNode node = selected;
      while (true) {
        node = next ? next(node) : prev(node);
        if (node == null || node == selected) return null;
        if (isOccurrenceNode(node)) return node;
      }
    }
    
    @Override
    public @NotNull String getNextOccurenceActionName() {
      return InspectionsBundle.message(ExperimentalUI.isNewUI() ? "inspection.action.go.next.new" : "inspection.action.go.next");
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return InspectionsBundle.message(ExperimentalUI.isNewUI() ? "inspection.action.go.prev.new" : "inspection.action.go.prev");
    }

    /**
     * Next node (depth-first pre-order traversal)
     * @param node  the node to start from
     * @return the next node, or null if the specified node was the last node in the tree.
     */
    private static InspectionTreeNode next(InspectionTreeNode node) {
      InspectionTreeNode.Children children = node.myChildren;
      // if node has children: take first child
      if (children != null && children.myChildren.length > 0) return children.myChildren[0];
      
      while (true) {
        // otherwise: take next sibling (or next sibling of parent (or next sibling of parent))
        InspectionTreeNode parent = node.myParent;
        if (parent == null) return TreeUtil.isCyclicScrollingAllowed() ? node : null;
        InspectionTreeNode.Children siblings = parent.myChildren;
        assert siblings != null;
        int index = Arrays.binarySearch(siblings.myChildren, node, InspectionResultsViewComparator.INSTANCE);
        assert index >= 0;
        index++;
        if (siblings.myChildren.length > index) return siblings.myChildren[index];
        node = parent;
      }
    }

    /**
     * Previous node (depth-first post-order traversal)
     * @param node  the node to start from
     * @return the previous node, or null if the specified node was the first (root) node in the tree.
     */
    private static InspectionTreeNode prev(InspectionTreeNode node) {
      InspectionTreeNode parent = node.myParent;
      InspectionTreeNode sibling;
      if (parent != null) {
        InspectionTreeNode.Children siblings = parent.myChildren;
        assert siblings != null;
        int index = Arrays.binarySearch(siblings.myChildren, node, InspectionResultsViewComparator.INSTANCE);
        assert index >= 0;
        index--;
        if (index < 0) return parent; // if no sibling: go up.
        sibling = siblings.myChildren[index];
      }
      else if (TreeUtil.isCyclicScrollingAllowed()) {
        sibling = node;
      }
      else {
        return null;
      }
      InspectionTreeNode.Children children = sibling.myChildren;
      while (children != null && children.myChildren.length > 0) {
        // if sibling: get its last child (of last child (of last child))
        sibling = children.myChildren[children.myChildren.length - 1];
        children = sibling.myChildren;
      }
      return sibling;
    }

    private InspectionTreeNode getSelectedNode() {
      TreePath path = getSelectionPath();
      if (path == null) return null;
      return (InspectionTreeNode)path.getLastPathComponent();
    }

    private boolean isOccurrenceNode(@NotNull InspectionTreeNode node) {
      if (node.isExcluded()) return false;
      if (node instanceof RefElementNode refNode) {
        if (!(refNode.getElement() instanceof RefElementImpl element) ||
            !element.isValid() ||
            !element.isSuspicious() ||
            !element.getRefManager().isDeclarationsFound() ||
            element.isEntry()) {
          return false;
        }
        InspectionToolWrapper<?, ?> wrapper = getToolWrapper(node);
        if (wrapper == null) wrapper = getSingleToolWrapper();
        return wrapper != null && wrapper.getShortName().contains("unused");
      }
      return node instanceof ProblemDescriptionNode;
    }

    private static @Nullable Navigatable createDescriptorForNode(@NotNull InspectionTreeNode node) {
      if (node.isExcluded()) return null;
      if (node instanceof RefElementNode refNode) {
        final RefEntity element = refNode.getElement();
        if (element == null || !element.isValid()) return null;
        if (element instanceof RefElement) {
          return getOpenFileDescriptor((RefElement)element);
        }
      }
      else if (node instanceof ProblemDescriptionNode problemNode) {
        boolean isValid = problemNode.isValid() && (!problemNode.isQuickFixAppliedFromView() ||
                                                    problemNode.calculateIsValid());
        return isValid
               ? navigate(problemNode.getDescriptor())
               : InspectionResultsViewUtil.getNavigatableForInvalidNode(problemNode);
      }
      return null;
    }

    private static @Nullable Navigatable navigate(final CommonProblemDescriptor descriptor) {
      return InspectionResultsView.getSelectedNavigatable(descriptor);
    }

    private static @Nullable Navigatable getOpenFileDescriptor(final @NotNull RefElement refElement) {
      PsiElement psiElement = refElement.getPsiElement();
      if (psiElement == null) return null;
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile == null) return null;
      VirtualFile file = containingFile.getVirtualFile();
      if (file == null) return null;
      return PsiNavigationSupport.getInstance().createNavigatable(refElement.getRefManager().getProject(), file,
                                                                  psiElement.getTextOffset());
    }
  }
}
