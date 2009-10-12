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
package com.intellij.ide.util.treeView;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Time;
import com.intellij.util.concurrency.WorkerThread;
import com.intellij.util.containers.HashSet;
import com.intellij.util.enumeration.EnumerationCopy;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.security.AccessControlException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AbstractTreeUi {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeBuilder");
  protected JTree myTree;// protected for TestNG
  @SuppressWarnings({"WeakerAccess"}) protected DefaultTreeModel myTreeModel;
  private AbstractTreeStructure myTreeStructure;
  private AbstractTreeUpdater myUpdater;
  private Comparator<NodeDescriptor> myNodeDescriptorComparator;
  private final Comparator<TreeNode> myNodeComparator = new Comparator<TreeNode>() {
    public int compare(TreeNode n1, TreeNode n2) {
      if (isLoadingNode(n1) || isLoadingNode(n2)) return 0;
      NodeDescriptor nodeDescriptor1 = getDescriptorFrom(((DefaultMutableTreeNode)n1));
      NodeDescriptor nodeDescriptor2 = getDescriptorFrom(((DefaultMutableTreeNode)n2));
      return myNodeDescriptorComparator != null
             ? myNodeDescriptorComparator.compare(nodeDescriptor1, nodeDescriptor2)
             : nodeDescriptor1.getIndex() - nodeDescriptor2.getIndex();
    }
  };
  private DefaultMutableTreeNode myRootNode;
  private final HashMap<Object, Object> myElementToNodeMap = new HashMap<Object, Object>();
  private final HashSet<DefaultMutableTreeNode> myUnbuiltNodes = new HashSet<DefaultMutableTreeNode>();
  private TreeExpansionListener myExpansionListener;
  private MySelectionListener mySelectionListener;

  private WorkerThread myWorker = null;
  private final Set<Runnable> myActiveWorkerTasks = new HashSet<Runnable>();

  private ProgressIndicator myProgress;
  private static final int WAIT_CURSOR_DELAY = 100;
  private AbstractTreeNode<Object> TREE_NODE_WRAPPER;
  private boolean myRootNodeWasInitialized = false;
  private final Map<Object, List<NodeAction>> myNodeActions = new HashMap<Object, List<NodeAction>>();
  private boolean myUpdateFromRootRequested;
  private boolean myWasEverShown;
  private boolean myUpdateIfInactive;

  private final Set<Object> myLoadingParents = new HashSet<Object>();
  private final Map<Object, List<NodeAction>> myNodeChildrenActions = new HashMap<Object, List<NodeAction>>();

  private long myClearOnHideDelay = -1;
  private ScheduledExecutorService ourClearanceService;
  private final Map<AbstractTreeUi, Long> ourUi2Countdown = Collections.synchronizedMap(new WeakHashMap<AbstractTreeUi, Long>());

  private final Set<Runnable> myDeferredSelections = new HashSet<Runnable>();
  private final Set<Runnable> myDeferredExpansions = new HashSet<Runnable>();

  private boolean myCanProcessDeferredSelections;

  private UpdaterTreeState myUpdaterState;
  private AbstractTreeBuilder myBuilder;

  private final Set<DefaultMutableTreeNode> myUpdatingChildren = new HashSet<DefaultMutableTreeNode>();
  private long myJanitorPollPeriod = Time.SECOND * 10;
  private boolean myCheckStructure = false;


  private boolean myCanYield = false;

  private final List<TreeUpdatePass> myYeildingPasses = new ArrayList<TreeUpdatePass>();

  private boolean myYeildingNow;

  private final Set<DefaultMutableTreeNode> myPendingNodeActions = new HashSet<DefaultMutableTreeNode>();
  private final Set<Runnable> myYeildingDoneRunnables = new HashSet<Runnable>();

  private final Alarm myBusyAlarm = new Alarm();
  private final Runnable myWaiterForReady = new Runnable() {
    public void run() {
      maybeSetBusyAndScheduleWaiterForReady(false);
    }
  };

  private final RegistryValue myYeildingUpdate = Registry.get("ide.tree.yeildingUiUpdate");
  private final RegistryValue myShowBusyIndicator = Registry.get("ide.tree.showBusyIndicator");
  private final RegistryValue myWaitForReadyTime = Registry.get("ide.tree.waitForReadyTimout");

  private boolean myWasEverIndexNotReady;
  private boolean myShowing;
  private FocusAdapter myFocusListener = new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
      maybeReady();
    }
  };
  private Set<DefaultMutableTreeNode> myNotForSmartExpand = new HashSet<DefaultMutableTreeNode>();
  private TreePath myRequestedExpand;
  private ActionCallback myInitialized = new ActionCallback();
  private Map<Object, ActionCallback> myReadyCallbacks = new WeakHashMap<Object, ActionCallback>();

  protected final void init(AbstractTreeBuilder builder,
                            JTree tree,
                            DefaultTreeModel treeModel,
                            AbstractTreeStructure treeStructure,
                            @Nullable Comparator<NodeDescriptor> comparator) {

    init(builder, tree, treeModel, treeStructure, comparator, true);
  }

  protected void init(AbstractTreeBuilder builder,
                      JTree tree,
                      DefaultTreeModel treeModel,
                      AbstractTreeStructure treeStructure,
                      @Nullable Comparator<NodeDescriptor> comparator,
                      boolean updateIfInactive) {
    myBuilder = builder;
    myTree = tree;
    myTreeModel = treeModel;
    TREE_NODE_WRAPPER = getBuilder().createSearchingTreeNodeWrapper();
    myTree.setModel(myTreeModel);
    setRootNode((DefaultMutableTreeNode)treeModel.getRoot());
    setTreeStructure(treeStructure);
    myNodeDescriptorComparator = comparator;
    myUpdateIfInactive = updateIfInactive;

    myExpansionListener = new MyExpansionListener();
    myTree.addTreeExpansionListener(myExpansionListener);

    mySelectionListener = new MySelectionListener();
    myTree.addTreeSelectionListener(mySelectionListener);

    setUpdater(getBuilder().createUpdater());
    myProgress = getBuilder().createProgressIndicator();
    Disposer.register(getBuilder(), getUpdater());

    final UiNotifyConnector uiNotify = new UiNotifyConnector(tree, new Activatable() {
      public void showNotify() {
        myShowing = true;
        myWasEverShown = true;
        if (!isReleased()) {
          activate(true);
        }
      }

      public void hideNotify() {
        myShowing = false;
        if (!isReleased()) {
          deactivate();
        }
      }
    });
    Disposer.register(getBuilder(), uiNotify);

    myTree.addFocusListener(myFocusListener);
  }


  private boolean isNodeActionsPending() {
    return !myNodeActions.isEmpty() || !myNodeChildrenActions.isEmpty();
  }

  private void clearNodeActions() {
    myNodeActions.clear();
    myNodeChildrenActions.clear();
  }

  private void maybeSetBusyAndScheduleWaiterForReady(boolean forcedBusy) {
    if (!myShowBusyIndicator.asBoolean() || !canYield()) return;

    if (myTree instanceof com.intellij.ui.treeStructure.Tree) {
      final com.intellij.ui.treeStructure.Tree tree = (Tree)myTree;
      final boolean isBusy = !isReady() || forcedBusy;
      if (isBusy && tree.isShowing()) {
        tree.setPaintBusy(true);
        myBusyAlarm.cancelAllRequests();
        myBusyAlarm.addRequest(myWaiterForReady, myWaitForReadyTime.asInteger());
      }
      else {
        tree.setPaintBusy(false);
      }
    }
  }

  private void initClearanceServiceIfNeeded() {
    if (ourClearanceService != null) return;

    ourClearanceService = ConcurrencyUtil.newSingleScheduledThreadExecutor("AbstractTreeBuilder's janitor");
    ourClearanceService.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        cleanUpAll();
      }
    }, myJanitorPollPeriod, myJanitorPollPeriod, TimeUnit.MILLISECONDS);
  }

  private void cleanUpAll() {
    final long now = System.currentTimeMillis();
    final AbstractTreeUi[] uis = ourUi2Countdown.keySet().toArray(new AbstractTreeUi[ourUi2Countdown.size()]);
    for (AbstractTreeUi eachUi : uis) {
      if (eachUi == null) continue;
      final Long timeToCleanup = ourUi2Countdown.get(eachUi);
      if (timeToCleanup == null) continue;
      if (now >= timeToCleanup.longValue()) {
        ourUi2Countdown.remove(eachUi);
        getBuilder().cleanUp();
      }
    }
  }

  protected void doCleanUp() {
    final Application app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) {
      cleanUpNow();
    }
    else {
      // we are not in EDT
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!isReleased()) {
            cleanUpNow();
          }
        }
      });
    }
  }

  private void disposeClearanceService() {
    try {
      if (ourClearanceService != null) {
        ourClearanceService.shutdown();
        ourClearanceService = null;
      }
    }
    catch (AccessControlException e) {
      LOG.warn(e);
    }
  }

  public void activate(boolean byShowing) {
    myCanProcessDeferredSelections = true;
    ourUi2Countdown.remove(this);

    if (!myWasEverShown || myUpdateFromRootRequested || myUpdateIfInactive) {
      getBuilder().updateFromRoot();
    }

    getUpdater().showNotify();

    myWasEverShown |= byShowing;
  }

  public void deactivate() {
    getUpdater().hideNotify();
    myBusyAlarm.cancelAllRequests();

    if (!myWasEverShown) return;

    if (isNodeActionsPending()) {
      cancelBackgroundLoading();
      myUpdateFromRootRequested = true;
    }

    if (getClearOnHideDelay() >= 0) {
      ourUi2Countdown.put(this, System.currentTimeMillis() + getClearOnHideDelay());
      initClearanceServiceIfNeeded();
    }
  }


  public void release() {
    if (isReleased()) return;

    myTree.removeTreeExpansionListener(myExpansionListener);
    myTree.removeTreeSelectionListener(mySelectionListener);
    myTree.removeFocusListener(myFocusListener);

    disposeNode(getRootNode());
    myElementToNodeMap.clear();
    getUpdater().cancelAllRequests();
    if (myWorker != null) {
      myWorker.dispose(true);
      clearWorkerTasks();
    }
    TREE_NODE_WRAPPER.setValue(null);
    if (myProgress != null) {
      myProgress.cancel();
    }
    disposeClearanceService();

    myTree = null;
    setUpdater(null);
    myWorker = null;
//todo [kirillk] afraid to do so just in release day, to uncomment
//    myTreeStructure = null;
    myBuilder = null;

    clearNodeActions();

    myDeferredSelections.clear();
    myDeferredExpansions.clear();
    myYeildingDoneRunnables.clear();
  }

  public boolean isReleased() {
    return myBuilder == null;
  }

  protected void doExpandNodeChildren(final DefaultMutableTreeNode node) {
    getTreeStructure().commit();
    getUpdater().addSubtreeToUpdate(node);
    getUpdater().performUpdate();
  }

  public final AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  public final JTree getTree() {
    return myTree;
  }

  @Nullable
  private NodeDescriptor getDescriptorFrom(DefaultMutableTreeNode node) {
    return (NodeDescriptor)node.getUserObject();
  }

  @Nullable
  public final DefaultMutableTreeNode getNodeForElement(Object element, final boolean validateAgainstStructure) {
    DefaultMutableTreeNode result = null;
    if (validateAgainstStructure) {
      int index = 0;
      while (true) {
        final DefaultMutableTreeNode node = findNode(element, index);
        if (node == null) break;

        if (isNodeValidForElement(element, node)) {
          result = node;
          break;
        }

        index++;
      }
    }
    else {
      result = getFirstNode(element);
    }


    if (result != null && !isNodeInStructure(result)) {
      disposeNode(result);
      result = null;
    }

    return result;
  }

  private boolean isNodeInStructure(DefaultMutableTreeNode node) {
    return TreeUtil.isAncestor(getRootNode(), node) && getRootNode() == myTreeModel.getRoot();
  }

  private boolean isNodeValidForElement(final Object element, final DefaultMutableTreeNode node) {
    return isSameHierarchy(element, node) || isValidChildOfParent(element, node);
  }

  private boolean isValidChildOfParent(final Object element, final DefaultMutableTreeNode node) {
    final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
    final Object parentElement = getElementFor(parent);
    if (!isInStructure(parentElement)) return false;

    if (parent instanceof ElementNode) {
      return ((ElementNode)parent).isValidChild(element);
    }
    else {
      for (int i = 0; i < parent.getChildCount(); i++) {
        final TreeNode child = parent.getChildAt(i);
        final Object eachElement = getElementFor(child);
        if (element.equals(eachElement)) return true;
      }
    }

    return false;
  }

  private boolean isSameHierarchy(Object eachParent, DefaultMutableTreeNode eachParentNode) {
    boolean valid = true;
    while (true) {
      if (eachParent == null) {
        valid = eachParentNode == null;
        break;
      }

      if (!eachParent.equals(getElementFor(eachParentNode))) {
        valid = false;
        break;
      }

      eachParent = getTreeStructure().getParentElement(eachParent);
      eachParentNode = (DefaultMutableTreeNode)eachParentNode.getParent();
    }
    return valid;
  }

  public final DefaultMutableTreeNode getNodeForPath(Object[] path) {
    DefaultMutableTreeNode node = null;
    for (final Object pathElement : path) {
      node = node == null ? getFirstNode(pathElement) : findNodeForChildElement(node, pathElement);
      if (node == null) {
        break;
      }
    }
    return node;
  }

  public final void buildNodeForElement(Object element) {
    getUpdater().performUpdate();
    DefaultMutableTreeNode node = getNodeForElement(element, false);
    if (node == null) {
      final java.util.List<Object> elements = new ArrayList<Object>();
      while (true) {
        element = getTreeStructure().getParentElement(element);
        if (element == null) {
          break;
        }
        elements.add(0, element);
      }

      for (final Object element1 : elements) {
        node = getNodeForElement(element1, false);
        if (node != null) {
          expand(node, true);
        }
      }
    }
  }

  public final void buildNodeForPath(Object[] path) {
    getUpdater().performUpdate();
    DefaultMutableTreeNode node = null;
    for (final Object pathElement : path) {
      node = node == null ? getFirstNode(pathElement) : findNodeForChildElement(node, pathElement);
      if (node != null && node != path[path.length - 1]) {
        expand(node, true);
      }
    }
  }

  public final void setNodeDescriptorComparator(Comparator<NodeDescriptor> nodeDescriptorComparator) {
    myNodeDescriptorComparator = nodeDescriptorComparator;
    List<Object> pathsToExpand = new ArrayList<Object>();
    List<Object> selectionPaths = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(getBuilder(), getRootNode(), pathsToExpand, selectionPaths, false);
    resortChildren(getRootNode());
    myTreeModel.nodeStructureChanged(getRootNode());
    TreeBuilderUtil.restorePaths(getBuilder(), pathsToExpand, selectionPaths, false);
  }

  protected AbstractTreeBuilder getBuilder() {
    return myBuilder;
  }

  private void resortChildren(DefaultMutableTreeNode node) {
    ArrayList<TreeNode> childNodes = TreeUtil.childrenToArray(node);
    node.removeAllChildren();
    sortChildren(node, childNodes);
    for (TreeNode childNode1 : childNodes) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
      node.add(childNode);
      resortChildren(childNode);
    }
  }

  protected final void initRootNode() {
    if (myUpdateIfInactive) {
      activate(false);
    } else {
      myUpdateFromRootRequested = true;
    }
  }

  private void initRootNodeNowIfNeeded(TreeUpdatePass pass) {
    if (myRootNodeWasInitialized) return;

    myRootNodeWasInitialized = true;

    Object rootElement = getTreeStructure().getRootElement();
    addNodeAction(rootElement, new NodeAction() {
      public void onReady(final DefaultMutableTreeNode node) {
        processDeferredActions();
      }
    }, false);


    NodeDescriptor rootDescriptor = getTreeStructure().createDescriptor(rootElement, null);
    getRootNode().setUserObject(rootDescriptor);
    update(rootDescriptor, false);
    if (getElementFromDescriptor(rootDescriptor) != null) {
      createMapping(getElementFromDescriptor(rootDescriptor), getRootNode());
    }


    insertLoadingNode(getRootNode(), true);

    boolean willUpdate = false;
    if (isAutoExpand(rootDescriptor)) {
      willUpdate = myUnbuiltNodes.contains(getRootNode());
      expand(getRootNode(), true);
    }
    if (!willUpdate) {
      updateNodeChildren(getRootNode(), pass, null, false, isAutoExpand(rootDescriptor), false, true);
    }
    if (getRootNode().getChildCount() == 0) {
      myTreeModel.nodeChanged(getRootNode());
    }
  }

  private boolean isAutoExpand(NodeDescriptor descriptor) {
    boolean autoExpand = false;

    if (descriptor != null) {
      autoExpand = getBuilder().isAutoExpandNode(descriptor);
    }

    if (!autoExpand && !myTree.isRootVisible()) {
      Object element = getElementFromDescriptor(descriptor);
      if (element != null && element.equals(getTreeStructure().getRootElement())) return true;
    }

    return autoExpand;
  }

  private boolean isAutoExpand(DefaultMutableTreeNode node) {
    return isAutoExpand(getDescriptorFrom(node));
  }

  private boolean update(final NodeDescriptor nodeDescriptor, boolean canBeNonEdt) {
    if (!canBeNonEdt && myWasEverShown) {
      assertIsDispatchThread();
    }

    if (isEdt() || !myWasEverShown || (!isEdt() && canBeNonEdt)) {
      return getBuilder().updateNodeDescriptor(nodeDescriptor);
    }
    else {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          if (!isReleased()) {
            getBuilder().updateNodeDescriptor(nodeDescriptor);
          }
        }
      });
      return true;
    }
  }

  private void assertIsDispatchThread() {
    if (isTreeShowing() && !isEdt()) {
      LOG.error("Must be in event-dispatch thread");
    }
  }

  private boolean isEdt() {
    return SwingUtilities.isEventDispatchThread();
  }

  private boolean isTreeShowing() {
    return myShowing;
  }

  private void assertNotDispatchThread() {
    if (isEdt()) {
      LOG.error("Must not be in event-dispatch thread");
    }
  }

  private void processDeferredActions() {
    processDeferredActions(myDeferredSelections);
    processDeferredActions(myDeferredExpansions);
  }

  private void processDeferredActions(Set<Runnable> actions) {
    final Runnable[] runnables = actions.toArray(new Runnable[actions.size()]);
    actions.clear();
    for (Runnable runnable : runnables) {
      runnable.run();
    }
  }

  public void doUpdateFromRoot() {
    updateSubtree(getRootNode(), false);
  }

  public ActionCallback doUpdateFromRootCB() {
    final ActionCallback cb = new ActionCallback();
    getUpdater().runAfterUpdate(new Runnable() {
      public void run() {
        cb.setDone();
      }
    });
    updateSubtree(getRootNode(), false);
    return cb;
  }

  public final void updateSubtree(DefaultMutableTreeNode node, boolean canSmartExpand) {
    updateSubtree(new TreeUpdatePass(node), canSmartExpand);
  }

  public final void updateSubtree(TreeUpdatePass pass, boolean canSmartExpand) {
    if (getUpdater() != null) {
      getUpdater().addSubtreeToUpdate(pass);
    }
    else {
      updateSubtreeNow(pass, canSmartExpand);
    }
  }

  final void updateSubtreeNow(TreeUpdatePass pass, boolean canSmartExpand) {
    maybeSetBusyAndScheduleWaiterForReady(true);

    initRootNodeNowIfNeeded(pass);

    final DefaultMutableTreeNode node = pass.getNode();

    if (!(node.getUserObject() instanceof NodeDescriptor)) return;

    setUpdaterState(new UpdaterTreeState(this)).beforeSubtreeUpdate();

    boolean forceUpdate = true;
    TreePath path = getPathFor(node);
    boolean invisible = !myTree.isExpanded(path) && (path.getParentPath() == null || !myTree.isExpanded(path.getParentPath()));
    
    if (invisible && myUnbuiltNodes.contains(node)) {
      forceUpdate = false;
    }

    updateNodeChildren(node, pass, null, false, canSmartExpand, forceUpdate, false);
  }

  private boolean isToBuildInBackground(NodeDescriptor descriptor) {
    return getTreeStructure().isToBuildChildrenInBackground(getBuilder().getTreeStructureElement(descriptor));
  }

  @NotNull
  UpdaterTreeState setUpdaterState(UpdaterTreeState state) {
    final UpdaterTreeState oldState = myUpdaterState;
    if (oldState == null) {
      myUpdaterState = state;
      return state;
    }
    else {
      oldState.addAll(state);
      return oldState;
    }
  }

  protected void doUpdateNode(DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    NodeDescriptor descriptor = getDescriptorFrom(node);
    Object prevElement = getElementFromDescriptor(descriptor);
    if (prevElement == null) return;
    boolean changes = update(descriptor, false);
    if (!isValid(descriptor)) {
      if (isInStructure(prevElement)) {
        getUpdater().addSubtreeToUpdateByElement(getTreeStructure().getParentElement(prevElement));
        return;
      }
    }
    if (changes) {
      updateNodeImageAndPosition(node, true);
    }
  }

  public Object getElementFromDescriptor(NodeDescriptor descriptor) {
    return getBuilder().getTreeStructureElement(descriptor);
  }

  private void updateNodeChildren(final DefaultMutableTreeNode node,
                                  final TreeUpdatePass pass,
                                  @Nullable LoadedChildren loadedChildren,
                                  boolean forcedNow,
                                  final boolean toSmartExpand,
                                  boolean forceUpdate,
                                  final boolean descriptorIsUpToDate) {
    getTreeStructure().commit();
    final boolean wasExpanded = myTree.isExpanded(new TreePath(node.getPath())) || isAutoExpand(node);
    final boolean wasLeaf = node.getChildCount() == 0;

    try {
      final NodeDescriptor descriptor = getDescriptorFrom(node);
      if (descriptor == null) {
        removeLoading(node, true);
        return;
      }

      boolean bgBuild = isToBuildInBackground(descriptor);
      boolean notRequiredToUpdateChildren = !forcedNow && !wasExpanded && !forceUpdate;
      LoadedChildren preloaded = loadedChildren;
      boolean descriptorWasUpdated = descriptorIsUpToDate;

      if (notRequiredToUpdateChildren) {
        if (myUnbuiltNodes.contains(node) && node.getChildCount() == 0) {
          insertLoadingNode(node, true);
        }
        return;
      }

      if (!forcedNow) {
        if (!bgBuild) {
          if (myUnbuiltNodes.contains(node)) {
            if (!descriptorWasUpdated) {
              update(descriptor, false);
              descriptorWasUpdated = true;
            }
            Pair<Boolean, LoadedChildren> unbuilt = processUnbuilt(node, descriptor, pass, wasExpanded, null);
            if (unbuilt.getFirst()) return;
            preloaded = unbuilt.getSecond();
          }
        }
      }


      boolean childForceUpdate = isChildNodeForceUpdate(node, forceUpdate, wasExpanded);

      if (!forcedNow && isToBuildInBackground(descriptor)) {
        queueBackgroundUpdate(node, descriptor, pass, canSmartExpand(node, toSmartExpand), wasExpanded, childForceUpdate, descriptorWasUpdated);
        return;
      } else {
        if (!descriptorWasUpdated) {
          update(descriptor, false);
        }

        updateNodeChildrenNow(node, pass, preloaded, toSmartExpand, wasExpanded, wasLeaf, childForceUpdate);
      }
    }
    finally {
      processNodeActionsIfReady(node);
    }
  }

  private boolean isChildNodeForceUpdate(DefaultMutableTreeNode node, boolean parentForceUpdate, boolean parentExpanded) {
    TreePath path = getPathFor(node);
    return parentForceUpdate && (parentExpanded || myTree.isExpanded(path));
  }

  private void updateNodeChildrenNow(final DefaultMutableTreeNode node, final TreeUpdatePass pass,
                                     final LoadedChildren preloadedChildren,
                                     final boolean toSmartExpand,
                                     final boolean wasExpanded,
                                     final boolean wasLeaf,
                                     final boolean forceUpdate) {
    final NodeDescriptor descriptor = getDescriptorFrom(node);

    final MutualMap<Object, Integer> elementToIndexMap = loadElementsFromStructure(descriptor, preloadedChildren);
    final LoadedChildren loadedChildren = preloadedChildren != null ? preloadedChildren : new LoadedChildren(elementToIndexMap.getKeys().toArray());


    addToUpdating(node);
    pass.setCurrentNode(node);

    final boolean canSmartExpand = canSmartExpand(node, toSmartExpand);

    processExistingNodes(node, elementToIndexMap, pass, canSmartExpand(node, toSmartExpand), forceUpdate, wasExpanded, preloadedChildren).doWhenDone(new Runnable() {
      public void run() {
        if (isDisposed(node)) {
          return;
        }

        removeLoading(node, false);

        final boolean expanded = isExpanded(node, wasExpanded);

        ArrayList<TreeNode> nodesToInsert = collectNodesToInsert(descriptor, elementToIndexMap, node, expanded, loadedChildren);
        insertNodesInto(nodesToInsert, node);
        updateNodesToInsert(nodesToInsert, pass, canSmartExpand, isChildNodeForceUpdate(node, forceUpdate, expanded));
        removeLoading(node, true);

        if (node.getChildCount() > 0) {
          if (expanded) {
            expand(node, canSmartExpand);
          }
        }

        removeFromUpdating(node);

        final Object element = getElementFor(node);
        addNodeAction(element, new NodeAction() {
          public void onReady(final DefaultMutableTreeNode node) {
            removeLoading(node, false);
          }
        }, false);

        processNodeActionsIfReady(node);
      }
    });
  }

  private boolean isDisposed(DefaultMutableTreeNode node) {
    return !node.isNodeAncestor((DefaultMutableTreeNode)myTree.getModel().getRoot());
  }

  private void expand(DefaultMutableTreeNode node, boolean canSmartExpand) {
    expand(new TreePath(node.getPath()), canSmartExpand);
  }

  private void expand(final TreePath path, boolean canSmartExpand) {
    if (path == null) return;


    final Object last = path.getLastPathComponent();
    boolean isLeaf = myTree.getModel().isLeaf(path.getLastPathComponent());
    final boolean isRoot = last == myTree.getModel().getRoot();
    final TreePath parent = path.getParentPath();
    if (isRoot && !myTree.isExpanded(path)) {
      if (myTree.isRootVisible() || myUnbuiltNodes.contains(last)) {
        insertLoadingNode((DefaultMutableTreeNode)last, false);
      }
      expandPath(path, canSmartExpand);
    }
    else if (myTree.isExpanded(path) || (isLeaf && parent != null && myTree.isExpanded(parent) && !myUnbuiltNodes.contains(last))) {
      if (last instanceof DefaultMutableTreeNode) {
        processNodeActionsIfReady((DefaultMutableTreeNode)last);
      }
    }
    else {
      if (isLeaf && myUnbuiltNodes.contains(last)) {
        insertLoadingNode((DefaultMutableTreeNode)last, true);
        expandPath(path, canSmartExpand);
      }
      else if (isLeaf && parent != null) {
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)parent.getLastPathComponent();
        if (parentNode != null) {
          addToUnbuilt(parentNode);
        }
        expandPath(parent, canSmartExpand);
      }
      else {
        expandPath(path, canSmartExpand);
      }
    }
  }

  private void addToUnbuilt(DefaultMutableTreeNode node) {
    myUnbuiltNodes.add(node);
  }

  private void removeFromUnbuilt(DefaultMutableTreeNode node) {
    myUnbuiltNodes.remove(node);
  }

  private Pair<Boolean, LoadedChildren> processUnbuilt(final DefaultMutableTreeNode node,
                                 final NodeDescriptor descriptor,
                                 final TreeUpdatePass pass,
                                 boolean isExpanded,
                                 final LoadedChildren loadedChildren) {
    if (!isExpanded && getBuilder().isAlwaysShowPlus(descriptor)) {
      return new Pair<Boolean, LoadedChildren>(true, null);
    }

    final Object element = getElementFor(node);

    final LoadedChildren children = loadedChildren != null ? loadedChildren : new LoadedChildren(getChildrenFor(element));

    boolean processed;

    if (children.getElements().size() == 0) {
      removeLoading(node, true);
      processed = true;
    }
    else {
      if (isAutoExpand(node)) {
        addNodeAction(getElementFor(node), new NodeAction() {
          public void onReady(final DefaultMutableTreeNode node) {
            final TreePath path = new TreePath(node.getPath());
            if (getTree().isExpanded(path) || children.getElements().size() == 0) {
              removeLoading(node, false);
            }
            else {
              maybeYeild(new ActiveRunnable() {
                public ActionCallback run() {
                  expand(element, null);
                  return new ActionCallback.Done();
                }
              }, pass, node);
            }
          }
        }, false);
      }
      processed = false;
    }

    processNodeActionsIfReady(node);

    return new Pair<Boolean, LoadedChildren>(processed, children);
  }

  private boolean removeIfLoading(TreeNode node) {
    if (isLoadingNode(node)) {
      moveSelectionToParentIfNeeded(node);
      removeNodeFromParent((MutableTreeNode)node, false);
      return true;
    }

    return false;
  }

  private void moveSelectionToParentIfNeeded(TreeNode node) {
    TreePath path = getPathFor(node);
    if (myTree.getSelectionModel().isPathSelected(path)) {
      TreePath parentPath = path.getParentPath();
      myTree.getSelectionModel().removeSelectionPath(path);
      if (parentPath != null) {
        myTree.getSelectionModel().addSelectionPath(parentPath);
      }
    }
  }

  //todo [kirillk] temporary consistency check
  private Object[] getChildrenFor(final Object element) {
    final Object[] passOne;
    try {
      passOne = getTreeStructure().getChildElements(element);
    }
    catch (IndexNotReadyException e) {
      if (!myWasEverIndexNotReady) {
        myWasEverIndexNotReady = true;
        LOG.warn("Tree is not dumb-mode-aware; treeBuilder=" + getBuilder() + " treeStructure=" + getTreeStructure());
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    if (!myCheckStructure) return passOne;

    final Object[] passTwo = getTreeStructure().getChildElements(element);

    final HashSet two = new HashSet(Arrays.asList(passTwo));

    if (passOne.length != passTwo.length) {
      LOG.error(
        "AbstractTreeStructure.getChildren() must either provide same objects or new objects but with correct hashCode() and equals() methods. Wrong parent element=" +
        element);
    }
    else {
      for (Object eachInOne : passOne) {
        if (!two.contains(eachInOne)) {
          LOG.error(
            "AbstractTreeStructure.getChildren() must either provide same objects or new objects but with correct hashCode() and equals() methods. Wrong parent element=" +
            element);
          break;
        }
      }
    }

    return passOne;
  }

  private void updateNodesToInsert(final ArrayList<TreeNode> nodesToInsert, TreeUpdatePass pass, boolean canSmartExpand, boolean forceUpdate) {
    for (TreeNode aNodesToInsert : nodesToInsert) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)aNodesToInsert;
      updateNodeChildren(childNode, pass, null, false, canSmartExpand, forceUpdate, true);
    }
  }

  private ActionCallback processExistingNodes(final DefaultMutableTreeNode node,
                                            final MutualMap<Object, Integer> elementToIndexMap,
                                            final TreeUpdatePass pass,
                                            final boolean canSmartExpand,
                                            final boolean forceUpdate,
                                            final boolean wasExpaned,
                                            final LoadedChildren preloaded) {

    final ArrayList<TreeNode> childNodes = TreeUtil.childrenToArray(node);
    return maybeYeild(new ActiveRunnable() {
      public ActionCallback run() {
        if (pass.isExpired()) return new ActionCallback.Rejected();
        if (childNodes.size() == 0) return new ActionCallback.Done();


        final ActionCallback result = new ActionCallback(childNodes.size());

        for (TreeNode each : childNodes) {
          final DefaultMutableTreeNode eachChild = (DefaultMutableTreeNode)each;
          if (isLoadingNode(eachChild)) {
            result.setDone();
            continue;
          }

          final boolean childForceUpdate = isChildNodeForceUpdate(eachChild, forceUpdate, wasExpaned);

          maybeYeild(new ActiveRunnable() {
            @Override
            public ActionCallback run() {
              return processExistingNode(eachChild, getDescriptorFrom(eachChild), node, elementToIndexMap, pass, canSmartExpand, childForceUpdate, preloaded);
            }
          }, pass, node).notify(result);

          if (result.isRejected()) {
            break;
          }
        }

        return result;
      }
    }, pass, node);
  }

  private boolean isRerunNeeded(TreeUpdatePass pass) {
    if (pass.isExpired()) return false;

    final boolean rerunBecauseTreeIsHidden = !pass.isExpired() && !isTreeShowing() && getUpdater().isInPostponeMode();

    return rerunBecauseTreeIsHidden || getUpdater().isRerunNeededFor(pass);
  }

  private ActionCallback maybeYeild(final ActiveRunnable processRunnable, final TreeUpdatePass pass, final DefaultMutableTreeNode node) {
    final ActionCallback result = new ActionCallback();

    if (isRerunNeeded(pass)) {
      getUpdater().addSubtreeToUpdate(pass);
      result.setRejected();
    }
    else {
      if (isToYieldUpdateFor(node)) {
        pass.setCurrentNode(node);
        yieldAndRun(new Runnable() {
          public void run() {
            if (pass.isExpired()) return;

            if (isRerunNeeded(pass)) {
              runDone(new Runnable() {
                public void run() {
                  if (!pass.isExpired()) {
                    getUpdater().addSubtreeToUpdate(pass);
                  }
                }
              });
              result.setRejected();
            }
            else {
              processRunnable.run().notify(result);
            }
          }
        }, pass);
      }
      else {
        processRunnable.run().notify(result);
      }
    }

    return result;
  }

  private void yieldAndRun(final Runnable runnable, final TreeUpdatePass pass) {
    myYeildingPasses.add(pass);
    myYeildingNow = true;
    yield(new Runnable() {
      public void run() {
        if (isReleased()) {
          return;
        }

        runOnYieldingDone(new Runnable() {
          public void run() {
            if (isReleased()) {
              return;
            }
            executeYieldingRequest(runnable, pass);
          }
        });
      }
    });
  }

  public boolean isYeildingNow() {
    return myYeildingNow;
  }

  private boolean hasSheduledUpdates() {
    return getUpdater().hasNodesToUpdate() || isLoadingInBackgroundNow();
  }

  public boolean isReady() {
    return isIdle() && !hasPendingWork();
  }

  public boolean hasPendingWork() {
    return hasNodesToUpdate() || (myUpdaterState != null && myUpdaterState.isProcessingNow());
  }

  public boolean isIdle() {
    return !isYeildingNow() && !isWorkerBusy() && (!hasSheduledUpdates() || getUpdater().isInPostponeMode());
  }

  private void executeYieldingRequest(Runnable runnable, TreeUpdatePass pass) {
    try {
      myYeildingPasses.remove(pass);
      runnable.run();
    }
    finally {
      maybeYeildingFinished();
    }
  }

  private void maybeYeildingFinished() {
    if (myYeildingPasses.size() == 0) {
      myYeildingNow = false;
      flushPendingNodeActions();
    }
  }

  void maybeReady() {
    if (isReleased()) return;

    if (isReady()) {
      if (myTree.isShowing() || myUpdateIfInactive) {
        myInitialized.setDone();
      }

      if (myTree.isShowing()) {
        if (getBuilder().isToEnsureSelectionOnFocusGained() && Registry.is("ide.tree.ensureSelectionOnFocusGained")) {
          TreeUtil.ensureSelection(myTree);
        }
      }

      if (myInitialized.isDone()) {
        for (ActionCallback each : getReadyCallbacks(true)) {
          each.setDone();
        }
      }
    }
  }

  private void flushPendingNodeActions() {
    final DefaultMutableTreeNode[] nodes = myPendingNodeActions.toArray(new DefaultMutableTreeNode[myPendingNodeActions.size()]);
    myPendingNodeActions.clear();

    for (DefaultMutableTreeNode each : nodes) {
      processNodeActionsIfReady(each);
    }

    final Runnable[] actions = myYeildingDoneRunnables.toArray(new Runnable[myYeildingDoneRunnables.size()]);
    for (Runnable each : actions) {
      if (!isYeildingNow()) {
        myYeildingDoneRunnables.remove(each);
        each.run();
      }
    }

    maybeReady();
  }

  protected void runOnYieldingDone(Runnable onDone) {
    getBuilder().runOnYeildingDone(onDone);
  }

  protected void yield(Runnable runnable) {
    getBuilder().yield(runnable);
  }

  private boolean isToYieldUpdateFor(final DefaultMutableTreeNode node) {
    if (!canYield()) return false;
    return getBuilder().isToYieldUpdateFor(node);
  }

  private MutualMap<Object, Integer> loadElementsFromStructure(final NodeDescriptor descriptor, @Nullable LoadedChildren preloadedChildren) {
    MutualMap<Object, Integer> elementToIndexMap = new MutualMap<Object, Integer>(true);
    List children = preloadedChildren != null ? preloadedChildren.getElements() : Arrays.asList(getChildrenFor(getBuilder().getTreeStructureElement(descriptor)));
    int index = 0;
    for (Object child : children) {
      if (!isValid(child)) continue;
      elementToIndexMap.put(child, Integer.valueOf(index));
      index++;
    }
    return elementToIndexMap;
  }

  private void expand(final DefaultMutableTreeNode node,
                      final NodeDescriptor descriptor,
                      final boolean wasLeaf,
                      final boolean canSmartExpand) {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    alarm.addRequest(new Runnable() {
      public void run() {
        myTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }, WAIT_CURSOR_DELAY);

    if (wasLeaf && isAutoExpand(descriptor)) {
      expand(node, canSmartExpand);
    }

    ArrayList<TreeNode> nodes = TreeUtil.childrenToArray(node);
    for (TreeNode node1 : nodes) {
      final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node1;
      if (isLoadingNode(childNode)) continue;
      NodeDescriptor childDescr = getDescriptorFrom(childNode);
      if (isAutoExpand(childDescr)) {
        addNodeAction(getElementFor(childNode), new NodeAction() {
          public void onReady(DefaultMutableTreeNode node) {
            expand(childNode, canSmartExpand);
          }
        }, false);
        addSubtreeToUpdate(childNode);
      }
    }

    int n = alarm.cancelAllRequests();
    if (n == 0) {
      myTree.setCursor(Cursor.getDefaultCursor());
    }
  }

  public static boolean isLoadingNode(final Object node) {
    return node instanceof LoadingNode;
  }

  private ArrayList<TreeNode> collectNodesToInsert(final NodeDescriptor descriptor, final MutualMap<Object, Integer> elementToIndexMap, DefaultMutableTreeNode parent, boolean addLoadingNode, @NotNull LoadedChildren loadedChildren) {
    ArrayList<TreeNode> nodesToInsert = new ArrayList<TreeNode>();
    final Collection<Object> allElements = elementToIndexMap.getKeys();

    for (Object child : allElements) {
      Integer index = elementToIndexMap.getValue(child);
      NodeDescriptor childDescr = loadedChildren.getDescriptor(child);
      boolean needToUpdate = false;
      if (childDescr == null) {
        childDescr = getTreeStructure().createDescriptor(child, descriptor);
        needToUpdate = true;
      }

      //noinspection ConstantConditions
      if (childDescr == null) {
        LOG.error("childDescr == null, treeStructure = " + getTreeStructure() + ", child = " + child);
        continue;
      }
      childDescr.setIndex(index.intValue());

      if (needToUpdate) {
        loadedChildren.putDescriptor(child, childDescr, update(childDescr, false));
      }

      Object element = getElementFromDescriptor(childDescr);
      if (element == null) {
        LOG.error("childDescr.getElement() == null, child = " + child + ", builder = " + this);
        continue;
      }

      DefaultMutableTreeNode node = getNodeForElement(element, false);
      if (node == null || node.getParent() != parent) {
        final DefaultMutableTreeNode childNode = createChildNode(childDescr);
        if (addLoadingNode || getBuilder().isAlwaysShowPlus(childDescr)) {
          insertLoadingNode(childNode, true);
        } else {
          addToUnbuilt(childNode);
        }
        nodesToInsert.add(childNode);
        createMapping(element, childNode);
      }
    }

    return nodesToInsert;
  }

  protected DefaultMutableTreeNode createChildNode(final NodeDescriptor descriptor) {
    return new ElementNode(this, descriptor);
  }

  protected boolean canYield() {
    return myCanYield && myYeildingUpdate.asBoolean();
  }

  public long getClearOnHideDelay() {
    return myClearOnHideDelay > 0 ? myClearOnHideDelay : Registry.intValue("ide.tree.clearOnHideTime");
  }

  public ActionCallback getInitialized() {
    return myInitialized;
  }

  public ActionCallback getReady(Object requestor) {
    if (isReady()) {
      return new ActionCallback.Done();
    } else {
      return addReadyCallback(requestor);
    }
  }

  private void addToUpdating(DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      myUpdatingChildren.add(node);
    }
  }

  private void removeFromUpdating(DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      myUpdatingChildren.remove(node);
    }
  }

  public boolean isUpdatingNow(DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      return myUpdatingChildren.contains(node);
    }
  }

  boolean hasUpdatingNow() {
    synchronized (myUpdatingChildren) {
      return myUpdatingChildren.size() > 0;
    }
  }

  public Map getNodeActions() {
    return myNodeActions;
  }

  public List<Object> getLoadedChildrenFor(Object element) {
     List<Object> result = new ArrayList<Object>();

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)findNodeByElement(element);
    if (node != null) {
      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode each = node.getChildAt(i);
        if (isLoadingNode(each)) continue;

        result.add(getElementFor(each));
      }
    }

    return result;
  }

  public boolean hasNodesToUpdate() {
    return getUpdater().hasNodesToUpdate() || hasUpdatingNow() || isLoadingInBackgroundNow();
  }

  public List<Object> getExpandedElements() {
    List<Object> result = new ArrayList<Object>();
    Enumeration<TreePath> enumeration = myTree.getExpandedDescendants(getPathFor(getRootNode()));
    while (enumeration.hasMoreElements()) {
      TreePath each = enumeration.nextElement();
      Object eachElement = getElementFor(each.getLastPathComponent());
      if (eachElement != null) {
        result.add(eachElement);
      }
    }

    return result;
  }

  static class ElementNode extends DefaultMutableTreeNode {

    Set<Object> myElements = new HashSet<Object>();
    AbstractTreeUi myUi;

    ElementNode(AbstractTreeUi ui, NodeDescriptor descriptor) {
      super(descriptor);
      myUi = ui;
    }

    @Override
    public void insert(final MutableTreeNode newChild, final int childIndex) {
      super.insert(newChild, childIndex);
      final Object element = myUi.getElementFor(newChild);
      if (element != null) {
        myElements.add(element);
      }
    }

    @Override
    public void remove(final int childIndex) {
      final TreeNode node = getChildAt(childIndex);
      super.remove(childIndex);
      final Object element = myUi.getElementFor(node);
      if (element != null) {
        myElements.remove(element);
      }
    }

    boolean isValidChild(Object childElement) {
      return myElements.contains(childElement);
    }

    @Override
    public String toString() {
      return String.valueOf(getUserObject());
    }
  }

  private boolean isUpdatingParent(DefaultMutableTreeNode kid) {
    DefaultMutableTreeNode eachParent = kid;
    while (eachParent != null) {
      if (isUpdatingNow(eachParent)) return true;
      eachParent = (DefaultMutableTreeNode)eachParent.getParent();
    }

    return false;
  }

  private boolean isLoadedInBackground(Object element) {
    synchronized (myLoadingParents) {
      return myLoadingParents.contains(element);
    }
  }

  private void addToLoadedInBackground(Object element) {
    synchronized (myLoadingParents) {
      myLoadingParents.add(element);
    }
  }

  private void removeFromLoadedInBackground(final Object element) {
    synchronized (myLoadingParents) {
      myLoadingParents.remove(element);
    }
  }

  private boolean isLoadingInBackgroundNow() {
    synchronized (myLoadingParents) {
      return myLoadingParents.size() > 0;
    }
  }

  private boolean queueBackgroundUpdate(final DefaultMutableTreeNode node,
                                        final NodeDescriptor descriptor,
                                        final TreeUpdatePass pass,
                                        final boolean canSmartExpand,
                                        final boolean wasExpanded,
                                        final boolean forceUpdate,
                                        final boolean descriptorIsUpToDate) {
    assertIsDispatchThread();

    final Object oldElementFromDescriptor = getElementFromDescriptor(descriptor);

    if (isLoadedInBackground(oldElementFromDescriptor)) return false;

    addToLoadedInBackground(oldElementFromDescriptor);

    if (!isNodeBeingBuilt(node)) {
      LoadingNode loadingNode = new LoadingNode(getLoadingNodeText());
      myTreeModel.insertNodeInto(loadingNode, node, node.getChildCount());
    }

    final Ref<LoadedChildren> children = new Ref<LoadedChildren>();
    final Ref<Object> elementFromDescriptor = new Ref<Object>();
    Runnable buildRunnable = new Runnable() {
      public void run() {
        if (isReleased()) {
          return;
        }

        if (!descriptorIsUpToDate) {
          update(descriptor, true);
        }

        Object element = getElementFromDescriptor(descriptor);
        if (element == null) {
          removeFromLoadedInBackground(oldElementFromDescriptor);
          return;
        }

        elementFromDescriptor.set(element);

        Object[] loadedElements = getChildrenFor(getBuilder().getTreeStructureElement(descriptor));
        LoadedChildren loaded = new LoadedChildren(loadedElements);
        for (Object each : loadedElements) {
          NodeDescriptor eachChildDescriptor = getTreeStructure().createDescriptor(each, descriptor);
          loaded.putDescriptor(each, eachChildDescriptor, eachChildDescriptor.update());
        }

        children.set(loaded);
      }
    };

    final DefaultMutableTreeNode[] nodeToProcessActions = new DefaultMutableTreeNode[1];
    Runnable updateRunnable = new Runnable() {
      public void run() {
        if (isReleased()) return;
        if (children.get() == null) return;

        if (isRerunNeeded(pass)) {
          removeFromLoadedInBackground(elementFromDescriptor.get());
          getUpdater().addSubtreeToUpdate(pass);
          return;
        }

        removeFromLoadedInBackground(elementFromDescriptor.get());

        if (myUnbuiltNodes.contains(node)) {
          Pair<Boolean, LoadedChildren> unbuilt = processUnbuilt(node, descriptor, pass, isExpanded(node, wasExpanded), children.get());
          if (unbuilt.getFirst()) {
            nodeToProcessActions[0] = node;
            return;
          }
        }

        updateNodeChildren(node, pass, children.get(), true, canSmartExpand, forceUpdate, true);


        if (isRerunNeeded(pass)) {
          getUpdater().addSubtreeToUpdate(pass);
          return;
        }

        Object element = elementFromDescriptor.get();

        if (element != null) {
          removeLoading(node, true);
          nodeToProcessActions[0] = node;
        }
      }
    };
    addTaskToWorker(buildRunnable, true, updateRunnable, new Runnable() {
      public void run() {
        if (nodeToProcessActions[0] != null) {
          processNodeActionsIfReady(nodeToProcessActions[0]);
        }
      }
    });
    return true;
  }

  private boolean isExpanded(DefaultMutableTreeNode node, boolean isExpanded) {
    return isExpanded || myTree.isExpanded(getPathFor(node));
  }

  private void removeLoading(DefaultMutableTreeNode parent, boolean removeFromUnbuilt) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      TreeNode child = parent.getChildAt(i);
      if (removeIfLoading(child)) {
        i--;
      }
    }

    if (removeFromUnbuilt) {
      removeFromUnbuilt(parent);
    }

    if (parent == getRootNode() && !myTree.isRootVisible() && parent.getChildCount() == 0) {
      insertLoadingNode(parent, false);
    }

    maybeReady();
  }

  private void processNodeActionsIfReady(final DefaultMutableTreeNode node) {
    if (isNodeBeingBuilt(node)) return;

    final Object o = node.getUserObject();
    if (!(o instanceof NodeDescriptor)) return;


    if (isYeildingNow()) {
      myPendingNodeActions.add(node);
      return;
    }

    final Object element = getBuilder().getTreeStructureElement((NodeDescriptor)o);

    processActions(node, element, myNodeActions);

    boolean childrenReady = !isLoadedInBackground(element);
    if (childrenReady) {
      processActions(node, element, myNodeChildrenActions);
    }

    if (!isUpdatingParent(node) && !isWorkerBusy()) {
      final UpdaterTreeState state = myUpdaterState;
      if (myNodeActions.size() == 0 && state != null && !state.isProcessingNow()) {
        if (!state.restore(childrenReady ? node : null)) {
          setUpdaterState(state);
        }
      }
    }

    maybeReady();
  }


  private void processActions(DefaultMutableTreeNode node, Object element, final Map<Object, List<NodeAction>> nodeActions) {
    final List<NodeAction> actions = nodeActions.get(element);
    if (actions != null) {
      nodeActions.remove(element);
      for (NodeAction each : actions) {
        each.onReady(node);
      }
    }
  }


  private boolean canSmartExpand(DefaultMutableTreeNode node, boolean canSmartExpand) {
    return !myNotForSmartExpand.contains(node) && canSmartExpand;
  }

  private void processSmartExpand(final DefaultMutableTreeNode node, final boolean canSmartExpand) {
    if (!getBuilder().isSmartExpand() || !canSmartExpand(node, canSmartExpand)) return;

    if (isNodeBeingBuilt(node)) {
      addNodeAction(getElementFor(node), new NodeAction() {
        public void onReady(DefaultMutableTreeNode node) {
          processSmartExpand(node, canSmartExpand);
        }
      }, true);
    }
    else {
      TreeNode child = getChildForSmartExpand(node);
      if (child != null) {
        final TreePath childPath = new TreePath(node.getPath()).pathByAddingChild(child);
        myTree.expandPath(childPath);
      }
    }
  }

  @Nullable
  private TreeNode getChildForSmartExpand(DefaultMutableTreeNode node) {
    int realChildCount = 0;
    TreeNode nodeToExpand = null;

    for (int i = 0; i < node.getChildCount(); i++) {
      TreeNode eachChild = node.getChildAt(i);

      if (!isLoadingNode(eachChild)) {
        realChildCount++;
        if (nodeToExpand == null) {
          nodeToExpand = eachChild;
        }
      }

      if (realChildCount > 1) {
        nodeToExpand = null;
        break;
      }
    }

    return nodeToExpand;
  }

  public boolean isLoadingChildrenFor(final Object nodeObject) {
    if (!(nodeObject instanceof DefaultMutableTreeNode)) return false;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodeObject;

    int loadingNodes = 0;
    for (int i = 0; i < Math.min(node.getChildCount(), 2); i++) {
      TreeNode child = node.getChildAt(i);
      if (isLoadingNode(child)) {
        loadingNodes++;
      }
    }
    return loadingNodes > 0 && loadingNodes == node.getChildCount();
  }

  private boolean isParentLoading(Object nodeObject) {
    if (!(nodeObject instanceof DefaultMutableTreeNode)) return false;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodeObject;

    TreeNode eachParent = node.getParent();

    while (eachParent != null) {
      eachParent = eachParent.getParent();
      if (eachParent instanceof DefaultMutableTreeNode) {
        final Object eachElement = getElementFor((DefaultMutableTreeNode)eachParent);
        if (isLoadedInBackground(eachElement)) return true;
      }
    }

    return false;
  }

  protected String getLoadingNodeText() {
    return IdeBundle.message("progress.searching");
  }

  private ActionCallback processExistingNode(final DefaultMutableTreeNode childNode,
                                          final NodeDescriptor childDescriptor,
                                          final DefaultMutableTreeNode parentNode,
                                          final MutualMap<Object, Integer> elementToIndexMap,
                                          TreeUpdatePass pass,
                                          final boolean canSmartExpand,
                                          boolean forceUpdate,
                                          LoadedChildren parentPreloadedChildren) {

    if (pass.isExpired()) {
      return new ActionCallback.Rejected();
    }

    NodeDescriptor childDesc = childDescriptor;


    if (childDesc == null) {
      pass.expire();
      return new ActionCallback.Rejected();
    }
    Object oldElement = getElementFromDescriptor(childDesc);
    if (oldElement == null) {
      pass.expire();
      return new ActionCallback.Rejected();
    }

    boolean changes;
    if (parentPreloadedChildren != null && parentPreloadedChildren.getDescriptor(oldElement) != null) {
      changes = parentPreloadedChildren.isUpdated(oldElement);      
    } else {
      changes = update(childDesc, false);
    }

    boolean forceRemapping = false;
    Object newElement = getElementFromDescriptor(childDesc);

    Integer index = newElement != null ? elementToIndexMap.getValue(getBuilder().getTreeStructureElement(childDesc)) : null;
    if (index != null) {
      final Object elementFromMap = elementToIndexMap.getKey(index);
      if (elementFromMap != newElement && elementFromMap.equals(newElement)) {
        if (isInStructure(elementFromMap) && isInStructure(newElement)) {
          if (parentNode.getUserObject() instanceof NodeDescriptor) {
            final NodeDescriptor parentDescriptor = getDescriptorFrom(parentNode);
            childDesc = getTreeStructure().createDescriptor(elementFromMap, parentDescriptor);
            childNode.setUserObject(childDesc);
            newElement = elementFromMap;
            forceRemapping = true;
            update(childDesc, false);
            changes = true;
          }
        }
      }

      if (childDesc.getIndex() != index.intValue()) {
        changes = true;
      }
      childDesc.setIndex(index.intValue());
    }

    if (index != null && changes) {
      updateNodeImageAndPosition(childNode, false);
    }
    if (!oldElement.equals(newElement) | forceRemapping) {
      removeMapping(oldElement, childNode, newElement);
      if (newElement != null) {
        createMapping(newElement, childNode);
      }
    }

    if (index == null) {
      int selectedIndex = -1;
      if (TreeBuilderUtil.isNodeOrChildSelected(myTree, childNode)) {
        selectedIndex = parentNode.getIndex(childNode);
      }

      if (childNode.getParent() instanceof DefaultMutableTreeNode) {
        final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)childNode.getParent();
        if (myTree.isExpanded(new TreePath(parent.getPath()))) {
          if (parent.getChildCount() == 1 && parent.getChildAt(0) == childNode) {
            insertLoadingNode(parent, false);
          }
        }
      }

      Object disposedElement = getElementFor(childNode);

      removeNodeFromParent(childNode, selectedIndex >= 0);
      disposeNode(childNode);

      adjustSelectionOnChildRemove(parentNode, selectedIndex, disposedElement);
    }
    else {
      elementToIndexMap.remove(getBuilder().getTreeStructureElement(childDesc));
      updateNodeChildren(childNode, pass, null, false, canSmartExpand, forceUpdate, true);
    }

    if (parentNode.equals(getRootNode())) {
      myTreeModel.nodeChanged(getRootNode());
    }

    return new ActionCallback.Done();
  }

  private void adjustSelectionOnChildRemove(DefaultMutableTreeNode parentNode, int selectedIndex, Object disposedElement) {
    DefaultMutableTreeNode node = getNodeForElement(disposedElement, false);
    if (node != null && isValidForSelectionAdjusting(node)) {
      Object newElement = getElementFor(node);
      addSelectionPath(getPathFor(node), true, getExpiredElementCondition(newElement));
      return;
    }


    if (selectedIndex >= 0) {
      if (parentNode.getChildCount() > 0) {
        if (parentNode.getChildCount() > selectedIndex) {
          TreeNode newChildNode = parentNode.getChildAt(selectedIndex);
          if (isValidForSelectionAdjusting(newChildNode)) {
            addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChildNode)), true, getExpiredElementCondition(disposedElement));
          }
        }
        else {
          TreeNode newChild = parentNode.getChildAt(parentNode.getChildCount() - 1);
          if (isValidForSelectionAdjusting(newChild)) {
            addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChild)), true, getExpiredElementCondition(disposedElement));
          }
        }
      }
      else {
        addSelectionPath(new TreePath(myTreeModel.getPathToRoot(parentNode)), true, getExpiredElementCondition(disposedElement));
      }
    }
  }

  private boolean isValidForSelectionAdjusting(TreeNode node) {
    if (!myTree.isRootVisible() && getRootNode() == node) return false;

    if (isLoadingNode(node)) return true;

    final Object elementInTree = getElementFor(node);
    if (elementInTree == null) return false;

    final TreeNode parentNode = node.getParent();
    final Object parentElementInTree = getElementFor(parentNode);
    if (parentElementInTree == null) return false;

    final Object parentElement = getTreeStructure().getParentElement(elementInTree);

    return parentElementInTree.equals(parentElement);
  }

  public Condition getExpiredElementCondition(final Object element) {
    return new Condition() {
      public boolean value(final Object o) {
        return isInStructure(element);
      }
    };
  }

  private void addSelectionPath(final TreePath path, final boolean isAdjustedSelection, final Condition isExpiredAdjustement) {
    doWithUpdaterState(new Runnable() {
      public void run() {
        TreePath toSelect = null;

        if (isLoadingNode(path.getLastPathComponent())) {
          final TreePath parentPath = path.getParentPath();
          if (parentPath != null) {
            toSelect = parentPath;
          }
        }
        else {
          toSelect = path;
        }

        if (toSelect != null) {
          myTree.addSelectionPath(toSelect);

          if (isAdjustedSelection && myUpdaterState != null) {
            final Object toSelectElement = getElementFor(toSelect.getLastPathComponent());
            myUpdaterState.addAdjustedSelection(toSelectElement, isExpiredAdjustement);
          }
        }
      }
    });
  }

  private static TreePath getPathFor(TreeNode node) {
    if (node instanceof DefaultMutableTreeNode) {
      return new TreePath(((DefaultMutableTreeNode)node).getPath());
    }
    else {
      ArrayList nodes = new ArrayList();
      TreeNode eachParent = node;
      while (eachParent != null) {
        nodes.add(eachParent);
        eachParent = eachParent.getParent();
      }

      return new TreePath(ArrayUtil.toObjectArray(nodes));
    }
  }


  private void removeNodeFromParent(final MutableTreeNode node, final boolean willAdjustSelection) {
    doWithUpdaterState(new Runnable() {
      public void run() {
        if (willAdjustSelection) {
          final TreePath path = getPathFor(node);
          if (myTree.isPathSelected(path)) {
            myTree.removeSelectionPath(path);
          }
        }

        myTreeModel.removeNodeFromParent(node);
      }
    });
  }

  private void expandPath(final TreePath path, final boolean canSmartExpand) {
    doWithUpdaterState(new Runnable() {
      public void run() {
        if (path.getLastPathComponent() instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          if (node.getChildCount() > 0 && !myTree.isExpanded(path)) {
            if (!canSmartExpand) {
              myNotForSmartExpand.add(node);
            }
            try {
              myRequestedExpand = path;
              myTree.expandPath(path);
              processSmartExpand(node, canSmartExpand);
            }
            finally {
              myNotForSmartExpand.remove(node);
              myRequestedExpand = null;
            }
          } else {
            processNodeActionsIfReady(node);
          }
        }
      }
    });
  }

  private void doWithUpdaterState(Runnable runnable) {
    if (myUpdaterState != null) {
      myUpdaterState.process(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected boolean doUpdateNodeDescriptor(final NodeDescriptor descriptor) {
    return descriptor.update();
  }

  private void makeLoadingOrLeafIfNoChildren(final DefaultMutableTreeNode node) {
    //Object[] children = null;
    //final boolean[] hasNoChildren = new boolean[1];
    //final NodeDescriptor descriptor = getDescriptorFrom(node);
    //if (!getBuilder().isAlwaysShowPlus(descriptor)) {
    //  Runnable updateRunnable = new Runnable() {
    //    public void run() {
    //      if (isReleased()) return;
    //
    //      if (hasNoChildren[0]) {
    //        //update(descriptor, false);
    //        removeLoading(node, false);
    //      }
    //    }
    //  };
    //
    //  if (isToBuildInBackground(descriptor)) {
    //    //Runnable buildRunnable = new Runnable() {
    //    //  public void run() {
    //    //    if (isReleased()) return;
    //    //
    //    //    update(descriptor, true);
    //    //    Object element = getBuilder().getTreeStructureElement(descriptor);
    //    //    if (element == null && !isValid(element)) return;
    //    //
    //    //    Object[] children = getChildrenFor(element);
    //    //    hasNoChildren[0] = children.length == 0;
    //    //  }
    //    //};
    //    //addTaskToWorker(buildRunnable, false, updateRunnable, new Runnable() {
    //    //  public void run() {
    //    //    processNodeActionsIfReady(node);
    //    //  }
    //    //});
    //  }
    //  else {
    //    children = getChildrenFor(getBuilder().getTreeStructureElement(descriptor));
    //    if (children.length == 0) return children;
    //  }
    //}
    //
    //insertLoadingNode(node, true);

    TreePath path = getPathFor(node);
    if (path == null) return;

    insertLoadingNode(node, true);

    final NodeDescriptor descriptor = getDescriptorFrom(node);
    if (getBuilder().isAlwaysShowPlus(descriptor)) return;


    TreePath parentPath = path.getParentPath();
    if (myTree.isVisible(path) || (parentPath != null && myTree.isExpanded(parentPath))) {
      if (myTree.isExpanded(path)) {
        getUpdater().addSubtreeToUpdate(node);
      } else {
        insertLoadingNode(node, false);
      }
    }
  }


  private boolean isValid(DefaultMutableTreeNode node) {
    if (node == null) return false;
    final Object object = node.getUserObject();
    if (object instanceof NodeDescriptor) {
      return isValid((NodeDescriptor)object);
    }

    return false;
  }

  private boolean isValid(NodeDescriptor descriptor) {
    if (descriptor == null) return false;
    return isValid(getElementFromDescriptor(descriptor));
  }

  private boolean isValid(Object element) {
    if (element instanceof ValidateableNode) {
      if (!((ValidateableNode)element).isValid()) return false;
    }
    return getBuilder().validateNode(element);
  }

  private void insertLoadingNode(final DefaultMutableTreeNode node, boolean addToUnbuilt) {
    if (!isLoadingChildrenFor(node)) {
      myTreeModel.insertNodeInto(new LoadingNode(), node, 0);
    }

    if (addToUnbuilt) {
      addToUnbuilt(node);
    }
  }


  protected void addTaskToWorker(@NotNull final Runnable bgReadActionRunnable,
                                 boolean first,
                                 @Nullable final Runnable edtPostRunnable,
                                 @Nullable final Runnable finalizeEdtRunnable) {
    registerWorkerTask(bgReadActionRunnable);

    final Runnable pooledThreadWithProgressRunnable = new Runnable() {
      public void run() {
        if (isReleased()) {
          return;
        }

        final AbstractTreeBuilder builder = getBuilder();

        builder.runBackgroundLoading(new Runnable() {
          public void run() {
            assertNotDispatchThread();

            if (isReleased()) {
              return;
            }

            try {
              bgReadActionRunnable.run();

              if (edtPostRunnable != null && !isReleased()) {
                builder.updateAfterLoadedInBackground(new Runnable() {
                  public void run() {
                    try {
                      assertIsDispatchThread();

                      if (isReleased()) {
                        return;
                      }

                      edtPostRunnable.run();
                    }
                    finally {
                      unregisterWorkerTask(bgReadActionRunnable, finalizeEdtRunnable);
                    }
                  }
                });
              }
              else {
                unregisterWorkerTask(bgReadActionRunnable, finalizeEdtRunnable);
              }
            }
            catch (ProcessCanceledException e) {
              unregisterWorkerTask(bgReadActionRunnable, finalizeEdtRunnable);
            }
            catch (Throwable t) {
              unregisterWorkerTask(bgReadActionRunnable, finalizeEdtRunnable);
              throw new RuntimeException(t);
            }
          }
        });
      }
    };

    Runnable pooledThreadRunnable = new Runnable() {
      public void run() {
        if (isReleased()) return;

        try {
          if (myProgress != null) {
            ProgressManager.getInstance().runProcess(pooledThreadWithProgressRunnable, myProgress);
          }
          else {
            pooledThreadWithProgressRunnable.run();
          }
        }
        catch (ProcessCanceledException e) {
          //ignore
        }
      }
    };

    if (myWorker == null || myWorker.isDisposed()) {
      myWorker = new WorkerThread("AbstractTreeBuilder.Worker", 1);
      myWorker.start();
      if (first) {
        myWorker.addTaskFirst(pooledThreadRunnable);
      }
      else {
        myWorker.addTask(pooledThreadRunnable);
      }
      myWorker.dispose(false);
    }
    else {
      if (first) {
        myWorker.addTaskFirst(pooledThreadRunnable);
      }
      else {
        myWorker.addTask(pooledThreadRunnable);
      }
    }
  }

  private void registerWorkerTask(Runnable runnable) {
    synchronized (myActiveWorkerTasks) {
      myActiveWorkerTasks.add(runnable);
    }
  }

  private void unregisterWorkerTask(Runnable runnable, @Nullable Runnable finalizeRunnable) {
    boolean wasRemoved;
    synchronized (myActiveWorkerTasks) {
      wasRemoved = myActiveWorkerTasks.remove(runnable);
    }

    if (wasRemoved && finalizeRunnable != null) {
      finalizeRunnable.run();
    }

    maybeReady();
  }

  public boolean isWorkerBusy() {
    synchronized (myActiveWorkerTasks) {
      return myActiveWorkerTasks.size() > 0;
    }
  }

  private void clearWorkerTasks() {
    synchronized (myActiveWorkerTasks) {
      myActiveWorkerTasks.clear();
    }
  }

  private void updateNodeImageAndPosition(final DefaultMutableTreeNode node, boolean updatePosition) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    NodeDescriptor descriptor = getDescriptorFrom(node);
    if (getElementFromDescriptor(descriptor) == null) return;

    boolean notified = false;
    if (updatePosition) {
      DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)node.getParent();
      if (parentNode != null) {
        int oldIndex = parentNode.getIndex(node);
        int newIndex = oldIndex;
        if (isLoadingChildrenFor(node.getParent()) || getBuilder().isChildrenResortingNeeded(descriptor)) {
          final ArrayList<TreeNode> children = new ArrayList<TreeNode>(parentNode.getChildCount());
          for (int i = 0; i < parentNode.getChildCount(); i++) {
            children.add(parentNode.getChildAt(i));
          }
          sortChildren(node, children);
          newIndex = children.indexOf(node);
        }

        if (oldIndex != newIndex) {
          List<Object> pathsToExpand = new ArrayList<Object>();
          List<Object> selectionPaths = new ArrayList<Object>();
          TreeBuilderUtil.storePaths(getBuilder(), node, pathsToExpand, selectionPaths, false);
          removeNodeFromParent(node, false);
          myTreeModel.insertNodeInto(node, parentNode, newIndex);
          TreeBuilderUtil.restorePaths(getBuilder(), pathsToExpand, selectionPaths, false);
          notified = true;
        }
        else {
          myTreeModel.nodeChanged(node);
          notified = true;
        }
      }
      else {
        myTreeModel.nodeChanged(node);
        notified = true;
      }
    }

    if (!notified) {
      myTreeModel.nodeChanged(node);
    }

  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  private void insertNodesInto(final ArrayList<TreeNode> toInsert, DefaultMutableTreeNode parentNode) {
    if (toInsert.isEmpty()) return;

    sortChildren(parentNode, toInsert);

    ArrayList<TreeNode> all = new ArrayList<TreeNode>(toInsert.size() + parentNode.getChildCount());
    all.addAll(toInsert);
    all.addAll(TreeUtil.childrenToArray(parentNode));

    sortChildren(parentNode, all);

    int[] newNodeIndices = new int[toInsert.size()];
    int eachNewNodeIndex = 0;
    for (int i = 0; i < toInsert.size(); i++) {
      TreeNode eachNewNode = toInsert.get(i);
      while (all.get(eachNewNodeIndex) != eachNewNode) eachNewNodeIndex++;
      newNodeIndices[i] = eachNewNodeIndex;
      parentNode.insert((MutableTreeNode)eachNewNode, eachNewNodeIndex);
    }

    myTreeModel.nodesWereInserted(parentNode, newNodeIndices);
  }

  private void sortChildren(DefaultMutableTreeNode node, ArrayList<TreeNode> children) {
    getBuilder().sortChildren(myNodeComparator, node, children);
  }

  private void disposeNode(DefaultMutableTreeNode node) {
    removeFromUpdating(node);
    removeFromUnbuilt(node);

    if (node.getChildCount() > 0) {
      for (DefaultMutableTreeNode _node = (DefaultMutableTreeNode)node.getFirstChild(); _node != null; _node = _node.getNextSibling()) {
        disposeNode(_node);
      }
    }
    if (isLoadingNode(node)) return;
    NodeDescriptor descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;
    final Object element = getElementFromDescriptor(descriptor);
    removeMapping(element, node, null);
    node.setUserObject(null);
    node.removeAllChildren();
  }

  public void addSubtreeToUpdate(final DefaultMutableTreeNode root) {
    addSubtreeToUpdate(root, null);
  }

  public void addSubtreeToUpdate(final DefaultMutableTreeNode root, Runnable runAfterUpdate) {
    getUpdater().runAfterUpdate(runAfterUpdate);
    getUpdater().addSubtreeToUpdate(root);
  }

  public boolean wasRootNodeInitialized() {
    return myRootNodeWasInitialized;
  }

  private boolean isRootNodeBuilt() {
    return myRootNodeWasInitialized && isNodeBeingBuilt(myRootNode);
  }

  public void select(final Object[] elements, @Nullable final Runnable onDone) {
    select(elements, onDone, false);
  }

  public void select(final Object[] elements, @Nullable final Runnable onDone, boolean addToSelection) {
    select(elements, onDone, addToSelection, false);
  }

  public void select(final Object[] elements, @Nullable final Runnable onDone, boolean addToSelection, boolean deferred) {
    _select(elements, onDone, addToSelection, true, false, true, deferred, false);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure) {

    _select(elements, onDone, addToSelection, checkCurrentSelection, checkIfInStructure, true, false, false);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure,
               final boolean scrollToVisible) {

    _select(elements, onDone, addToSelection, checkCurrentSelection, checkIfInStructure, scrollToVisible, false, false);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure,
               final boolean scrollToVisible,
               final boolean deferred,
               final boolean canSmartExpand) {

    boolean willAffectSelection = elements.length > 0 || (elements.length == 0 && addToSelection);
    if (!willAffectSelection) {
      runDone(onDone);
      return;
    }

    final boolean oldCanProcessDeferredSelection = myCanProcessDeferredSelections;

    if (!deferred && wasRootNodeInitialized() && willAffectSelection) {
      myCanProcessDeferredSelections = false;
    }

    if (!checkDeferred(deferred, onDone)) return;

    if (!deferred && oldCanProcessDeferredSelection && !myCanProcessDeferredSelections) {
      getTree().clearSelection();
    }


    runDone(new Runnable() {
      public void run() {
        if (!checkDeferred(deferred, onDone)) return;

        final Set<Object> currentElements = getSelectedElements();

        if (checkCurrentSelection && currentElements.size() > 0 && elements.length == currentElements.size()) {
          boolean runSelection = false;
          for (Object eachToSelect : elements) {
            if (!currentElements.contains(eachToSelect)) {
              runSelection = true;
              break;
            }
          }

          if (!runSelection) {
            if (elements.length > 0) {
              selectVisible(elements[0], onDone, true, true, scrollToVisible);
            }
            return;
          }
        }

        Set<Object> toSelect = new HashSet<Object>();
        myTree.clearSelection();
        toSelect.addAll(Arrays.asList(elements));
        if (addToSelection) {
          toSelect.addAll(currentElements);
        }

        if (checkIfInStructure) {
          final Iterator<Object> allToSelect = toSelect.iterator();
          while (allToSelect.hasNext()) {
            Object each = allToSelect.next();
            if (!isInStructure(each)) {
              allToSelect.remove();
            }
          }
        }

        final Object[] elementsToSelect = ArrayUtil.toObjectArray(toSelect);

        if (wasRootNodeInitialized()) {
          final int[] originalRows = myTree.getSelectionRows();
          if (!addToSelection) {
            myTree.clearSelection();
          }
          addNext(elementsToSelect, 0, new Runnable() {
            public void run() {
              if (getTree().isSelectionEmpty()) {
                restoreSelection(currentElements);
              }
              runDone(onDone);
            }
          }, originalRows, deferred, scrollToVisible, canSmartExpand);
        }
        else {
          addToDeferred(elementsToSelect, onDone);
        }
      }
    });
  }

  private void restoreSelection(Set<Object> selection) {
    for (Object each : selection) {
      DefaultMutableTreeNode node = getNodeForElement(each, false);
      if (node != null && isValidForSelectionAdjusting(node)) {
        addSelectionPath(getPathFor(node), false, null);
      }
    }
  }


  private void addToDeferred(final Object[] elementsToSelect, final Runnable onDone) {
    myDeferredSelections.clear();
    myDeferredSelections.add(new Runnable() {
      public void run() {
        select(elementsToSelect, onDone, false, true);
      }
    });
  }

  private boolean checkDeferred(boolean isDeferred, @Nullable Runnable onDone) {
    if (!isDeferred || myCanProcessDeferredSelections || !wasRootNodeInitialized()) {
      return true;
    }
    else {
      runDone(onDone);
      return false;
    }
  }

  @NotNull
  final Set<Object> getSelectedElements() {
    final TreePath[] paths = myTree.getSelectionPaths();

    Set<Object> result = new HashSet<Object>();
    if (paths != null) {
      for (TreePath eachPath : paths) {
        if (eachPath.getLastPathComponent() instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode eachNode = (DefaultMutableTreeNode)eachPath.getLastPathComponent();
          final Object eachElement = getElementFor(eachNode);
          if (eachElement != null) {
            result.add(eachElement);
          }
        }
      }
    }
    return result;
  }


  private void addNext(final Object[] elements,
                       final int i,
                       @Nullable final Runnable onDone,
                       final int[] originalRows,
                       final boolean deferred,
                       final boolean scrollToVisible,
                       final boolean canSmartExpand) {
    if (i >= elements.length) {
      if (myTree.isSelectionEmpty()) {
        myTree.setSelectionRows(originalRows);
      }
      runDone(onDone);
    }
    else {
      if (!checkDeferred(deferred, onDone)) {
        return;
      }

      doSelect(elements[i], new Runnable() {
        public void run() {
          if (!checkDeferred(deferred, onDone)) return;

          addNext(elements, i + 1, onDone, originalRows, deferred, scrollToVisible, canSmartExpand);
        }
      }, true, deferred, i == 0, scrollToVisible, canSmartExpand);
    }
  }

  public void select(final Object element, @Nullable final Runnable onDone) {
    select(element, onDone, false);
  }

  public void select(final Object element, @Nullable final Runnable onDone, boolean addToSelection) {
    _select(new Object[]{element}, onDone, addToSelection, true, false);
  }

  private void doSelect(final Object element,
                        final Runnable onDone,
                        final boolean addToSelection,
                        final boolean deferred,
                        final boolean canBeCentered,
                        final boolean scrollToVisible,
                        boolean canSmartExpand) {
    final Runnable _onDone = new Runnable() {
      public void run() {
        if (!checkDeferred(deferred, onDone)) return;
        selectVisible(element, onDone, addToSelection, canBeCentered, scrollToVisible);
      }
    };
    _expand(element, _onDone, true, false, canSmartExpand);
  }

  public void scrollSelectionToVisible(@Nullable Runnable onDone, boolean shouldBeCentered) {
    int[] rows = myTree.getSelectionRows();
    if (rows == null || rows.length == 0) {
      runDone(onDone);
      return;
    }


    Object toSelect = null;
    for (int eachRow : rows) {
      TreePath path = myTree.getPathForRow(eachRow);
      toSelect = getElementFor(path.getLastPathComponent());
      if (toSelect != null) break;
    }

    if (toSelect != null) {
      selectVisible(toSelect, onDone, true, shouldBeCentered, true);
    }
  }

  private void selectVisible(Object element, final Runnable onDone, boolean addToSelection, boolean canBeCentered, final boolean scroll) {
    final DefaultMutableTreeNode toSelect = getNodeForElement(element, false);

    if (toSelect == null) {
      runDone(onDone);
      return;
    }

    if (getRootNode() == toSelect && !myTree.isRootVisible()) {
      runDone(onDone);
      return;
    }

    final int row = myTree.getRowForPath(new TreePath(toSelect.getPath()));

    if (myUpdaterState != null) {
      myUpdaterState.addSelection(element);
    }

    if (Registry.is("ide.tree.autoscrollToVCenter") && canBeCentered) {
      runDone(new Runnable() {
        public void run() {
          TreeUtil.showRowCentered(myTree, row, false, scroll).doWhenDone(new Runnable() {
            public void run() {
              runDone(onDone);
            }
          });
        }
      });
    }
    else {
      TreeUtil.showAndSelect(myTree, row - 2, row + 2, row, -1, addToSelection, scroll).doWhenDone(new Runnable() {
        public void run() {
          runDone(onDone);
        }
      });
    }
  }

  public void expand(final Object element, @Nullable final Runnable onDone) {
    expand(new Object[] {element}, onDone);
  }

  public void expand(final Object[] element, @Nullable final Runnable onDone) {
    expand(element, onDone, false);
  }


  void expand(final Object element, @Nullable final Runnable onDone, boolean checkIfInStructure) {
    _expand(new Object[]{element}, onDone == null ? new EmptyRunnable() : onDone, false, checkIfInStructure, false);
  }

  void expand(final Object[] element, @Nullable final Runnable onDone, boolean checkIfInStructure) {
    _expand(element, onDone == null ? new EmptyRunnable() : onDone, false, checkIfInStructure, false);
  }

  void _expand(final Object[] element,
               @NotNull final Runnable onDone,
               final boolean parentsOnly,
               final boolean checkIfInStructure,
               final boolean canSmartExpand) {

    runDone(new Runnable() {
      public void run() {
        if (element.length == 0) {
          runDone(onDone);
          return;
        }

        if (myUpdaterState != null) {
          myUpdaterState.clearExpansion();
        }


        final ActionCallback done = new ActionCallback(element.length);
        done.doWhenDone(new Runnable() {
          public void run() {
            runDone(onDone);
          }
        });

        for (final Object toExpand : element) {
          _expand(toExpand, new Runnable() {
            public void run() {
              done.setDone();
            }
          }, parentsOnly, checkIfInStructure, canSmartExpand);
        }
      }
    });
  }

  public void collapseChildren(final Object element, @Nullable final Runnable onDone) {
    runDone(new Runnable() {
      public void run() {
        final DefaultMutableTreeNode node = getNodeForElement(element, false);
        if (node != null) {
          getTree().collapsePath(new TreePath(node.getPath()));
          runDone(onDone);
        }
      }
    });
  }

  private void runDone(@Nullable Runnable done) {
    if (isReleased()) return;
    if (done == null) return;

    if (isYeildingNow()) {
      if (!myYeildingDoneRunnables.contains(done)) {
        myYeildingDoneRunnables.add(done);
      }
    }
    else {
      done.run();
    }
  }

  private void _expand(final Object element,
                       @NotNull final Runnable onDone,
                       final boolean parentsOnly,
                       boolean checkIfInStructure,
                       boolean canSmartExpand) {

    if (checkIfInStructure && !isInStructure(element)) {
      runDone(onDone);
      return;
    }

    if (wasRootNodeInitialized()) {
      List<Object> kidsToExpand = new ArrayList<Object>();
      Object eachElement = element;
      DefaultMutableTreeNode firstVisible = null;
      while (true) {
        if (!isValid(eachElement)) break;

        firstVisible = getNodeForElement(eachElement, true);
        if (eachElement != element || !parentsOnly) {
          assert !kidsToExpand.contains(eachElement) :
            "Not a valid tree structure, walking up the structure gives many entries for element=" +
            eachElement +
            ", root=" +
            getTreeStructure().getRootElement();
          kidsToExpand.add(eachElement);
        }
        if (firstVisible != null) break;
        eachElement = getTreeStructure().getParentElement(eachElement);
        if (eachElement == null) {
          firstVisible = null;
          break;
        }
      }


      if (firstVisible == null) {
        runDone(onDone);
      }
      else if (kidsToExpand.size() == 0) {
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)firstVisible.getParent();
        if (parentNode != null) {
          final TreePath parentPath = new TreePath(parentNode.getPath());
          if (!myTree.isExpanded(parentPath)) {
            expand(parentPath, canSmartExpand);
          }
        }
        runDone(onDone);
      }
      else {
        processExpand(firstVisible, kidsToExpand, kidsToExpand.size() - 1, onDone, canSmartExpand);
      }
    }
    else {
      deferExpansion(element, onDone, parentsOnly, canSmartExpand);
    }
  }

  private void deferExpansion(final Object element, final Runnable onDone, final boolean parentsOnly, final boolean canSmartExpand) {
    myDeferredExpansions.add(new Runnable() {
      public void run() {
        _expand(element, onDone, parentsOnly, false, canSmartExpand);
      }
    });
  }

  private void processExpand(final DefaultMutableTreeNode toExpand,
                             final List kidsToExpand,
                             final int expandIndex,
                             @NotNull final Runnable onDone,
                             final boolean canSmartExpand) {

    final Object element = getElementFor(toExpand);
    if (element == null) {
      runDone(onDone);
      return;
    }

    addNodeAction(element, new NodeAction() {
      public void onReady(final DefaultMutableTreeNode node) {

        if (node.getChildCount() > 0 && !myTree.isExpanded(new TreePath(node.getPath()))) {
          if (!isAutoExpand(node)) {
            expand(node, canSmartExpand);
          }
        }

        if (expandIndex <= 0) {
          runDone(onDone);
          return;
        }

        final DefaultMutableTreeNode nextNode = getNodeForElement(kidsToExpand.get(expandIndex - 1), false);
        if (nextNode != null) {
          processExpand(nextNode, kidsToExpand, expandIndex - 1, onDone, canSmartExpand);
        }
        else {
          runDone(onDone);
        }
      }
    }, true);


    if (myTree.isExpanded(getPathFor(toExpand)) && !myUnbuiltNodes.contains(toExpand)) {
      processNodeActionsIfReady(toExpand);
    } else {
      if (!myUnbuiltNodes.contains(toExpand)) {
        getUpdater().addSubtreeToUpdate(toExpand);        
      } else {
        expand(toExpand, canSmartExpand);
      }
    }
  }


  private String asString(DefaultMutableTreeNode node) {
    if (node == null) return null;

    StringBuffer children = new StringBuffer(node.toString());
    children.append(" [");
    for (int i = 0; i < node.getChildCount(); i++) {
      children.append(node.getChildAt(i));
      if (i < node.getChildCount() - 1) {
        children.append(",");
      }
    }
    children.append("]");

    return children.toString();
  }

  @Nullable
  public Object getElementFor(Object node) {
    if (!(node instanceof DefaultMutableTreeNode)) return null;
    return getElementFor((DefaultMutableTreeNode)node);
  }

  @Nullable
  Object getElementFor(DefaultMutableTreeNode node) {
    if (node != null) {
      final Object o = node.getUserObject();
      if (o instanceof NodeDescriptor) {
        return getElementFromDescriptor(((NodeDescriptor)o));
      }
    }

    return null;
  }

  public final boolean isNodeBeingBuilt(final TreePath path) {
    return isNodeBeingBuilt(path.getLastPathComponent());
  }

  public final boolean isNodeBeingBuilt(Object node) {
    if (isParentLoading(node) || isLoadingParent(node)) return true;

    final boolean childrenAreNoLoadedYet = myUnbuiltNodes.contains(node);
    if (childrenAreNoLoadedYet) {
      if (node instanceof DefaultMutableTreeNode) {
        final TreePath nodePath = new TreePath(((DefaultMutableTreeNode)node).getPath());
        if (!myTree.isExpanded(nodePath)) return false;
      }

      return true;
    }


    return false;
  }

  private boolean isLoadingParent(Object node) {
    if (!(node instanceof DefaultMutableTreeNode)) return false;
    return isLoadedInBackground(getElementFor((DefaultMutableTreeNode)node));
  }

  public void setTreeStructure(final AbstractTreeStructure treeStructure) {
    myTreeStructure = treeStructure;
    clearUpdaterState();
  }

  public AbstractTreeUpdater getUpdater() {
    return myUpdater;
  }

  public void setUpdater(final AbstractTreeUpdater updater) {
    myUpdater = updater;
    if (updater != null && myUpdateIfInactive) {
      updater.showNotify();
    }
  }

  public DefaultMutableTreeNode getRootNode() {
    return myRootNode;
  }

  public void setRootNode(@NotNull final DefaultMutableTreeNode rootNode) {
    myRootNode = rootNode;
  }

  private void dropUpdaterStateIfExternalChange() {
    if (myUpdaterState != null && !myUpdaterState.isProcessingNow()) {
      clearUpdaterState();
    }
  }

  void clearUpdaterState() {
    myUpdaterState = null;
  }

  private void createMapping(Object element, DefaultMutableTreeNode node) {
    if (!myElementToNodeMap.containsKey(element)) {
      myElementToNodeMap.put(element, node);
    }
    else {
      final Object value = myElementToNodeMap.get(element);
      final List<DefaultMutableTreeNode> nodes;
      if (value instanceof DefaultMutableTreeNode) {
        nodes = new ArrayList<DefaultMutableTreeNode>();
        nodes.add((DefaultMutableTreeNode)value);
        myElementToNodeMap.put(element, nodes);
      }
      else {
        nodes = (List<DefaultMutableTreeNode>)value;
      }
      nodes.add(node);
    }
  }

  private void removeMapping(Object element, DefaultMutableTreeNode node, @Nullable Object elementToPutNodeActionsFor) {
    final Object value = myElementToNodeMap.get(element);
    if (value != null) {
      if (value instanceof DefaultMutableTreeNode) {
        if (value.equals(node)) {
          myElementToNodeMap.remove(element);
        }
      }
      else {
        List<DefaultMutableTreeNode> nodes = (List<DefaultMutableTreeNode>)value;
        final boolean reallyRemoved = nodes.remove(node);
        if (reallyRemoved) {
          if (nodes.isEmpty()) {
            myElementToNodeMap.remove(element);
          }
        }
      }
    }

    remapNodeActions(element, elementToPutNodeActionsFor);
  }

  private void remapNodeActions(Object element, Object elementToPutNodeActionsFor) {
    _remapNodeActions(element, elementToPutNodeActionsFor, myNodeActions);
    _remapNodeActions(element, elementToPutNodeActionsFor, myNodeChildrenActions);
  }

  private void _remapNodeActions(Object element, Object elementToPutNodeActionsFor, final Map<Object, List<NodeAction>> nodeActions) {
    final List<NodeAction> actions = nodeActions.get(element);
    nodeActions.remove(element);

    if (elementToPutNodeActionsFor != null && actions != null) {
      nodeActions.put(elementToPutNodeActionsFor, actions);
    }
  }

  private DefaultMutableTreeNode getFirstNode(Object element) {
    return findNode(element, 0);
  }

  private DefaultMutableTreeNode findNode(final Object element, int startIndex) {
    final Object value = getBuilder().findNodeByElement(element);
    if (value == null) {
      return null;
    }
    if (value instanceof DefaultMutableTreeNode) {
      return startIndex == 0 ? (DefaultMutableTreeNode)value : null;
    }
    final List<DefaultMutableTreeNode> nodes = (List<DefaultMutableTreeNode>)value;
    return startIndex < nodes.size() ? nodes.get(startIndex) : null;
  }

  protected Object findNodeByElement(Object element) {
    if (myElementToNodeMap.containsKey(element)) {
      return myElementToNodeMap.get(element);
    }

    try {
      TREE_NODE_WRAPPER.setValue(element);
      return myElementToNodeMap.get(TREE_NODE_WRAPPER);
    }
    finally {
      TREE_NODE_WRAPPER.setValue(null);
    }
  }

  private DefaultMutableTreeNode findNodeForChildElement(DefaultMutableTreeNode parentNode, Object element) {
    final Object value = myElementToNodeMap.get(element);
    if (value == null) {
      return null;
    }

    if (value instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode elementNode = (DefaultMutableTreeNode)value;
      return parentNode.equals(elementNode.getParent()) ? elementNode : null;
    }

    final List<DefaultMutableTreeNode> allNodesForElement = (List<DefaultMutableTreeNode>)value;
    for (final DefaultMutableTreeNode elementNode : allNodesForElement) {
      if (parentNode.equals(elementNode.getParent())) {
        return elementNode;
      }
    }

    return null;
  }

  public void cancelBackgroundLoading() {
    if (myWorker != null) {
      myWorker.cancelTasks();
      clearWorkerTasks();
    }

    clearNodeActions();
  }

  private void addNodeAction(Object element, NodeAction action, boolean shouldChildrenBeReady) {
    _addNodeAction(element, action, myNodeActions);
    if (shouldChildrenBeReady) {
      _addNodeAction(element, action, myNodeChildrenActions);
    }
  }


  private void _addNodeAction(Object element, NodeAction action, Map<Object, List<NodeAction>> map) {
    maybeSetBusyAndScheduleWaiterForReady(true);
    List<NodeAction> list = map.get(element);
    if (list == null) {
      list = new ArrayList<NodeAction>();
      map.put(element, list);
    }
    list.add(action);
  }


  private void cleanUpNow() {
    if (isReleased()) return;

    final UpdaterTreeState state = new UpdaterTreeState(this);

    myTree.collapsePath(new TreePath(myTree.getModel().getRoot()));
    myTree.clearSelection();
    getRootNode().removeAllChildren();

    myRootNodeWasInitialized = false;
    clearNodeActions();
    myElementToNodeMap.clear();
    myDeferredSelections.clear();
    myDeferredExpansions.clear();
    myLoadingParents.clear();
    myUnbuiltNodes.clear();
    myUpdateFromRootRequested = true;

    if (myWorker != null) {
      Disposer.dispose(myWorker);
      myWorker = null;
    }

    myTree.invalidate();

    state.restore(null);
  }

  public AbstractTreeUi setClearOnHideDelay(final long clearOnHideDelay) {
    myClearOnHideDelay = clearOnHideDelay;
    return this;
  }

  public void setJantorPollPeriod(final long time) {
    myJanitorPollPeriod = time;
  }

  public void setCheckStructure(final boolean checkStructure) {
    myCheckStructure = checkStructure;
  }

  private class MySelectionListener implements TreeSelectionListener {
    public void valueChanged(final TreeSelectionEvent e) {
      dropUpdaterStateIfExternalChange();
    }
  }


  private class MyExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      dropUpdaterStateIfExternalChange();

      TreePath path = event.getPath();

      if (myRequestedExpand != null && !myRequestedExpand.equals(path)) return;

      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();

      if (!myUnbuiltNodes.contains(node)) {
        removeLoading(node, false);

        boolean hasUnbuiltChildren = false;
        for (int i = 0; i < node.getChildCount(); i++) {
          DefaultMutableTreeNode each = (DefaultMutableTreeNode)node.getChildAt(i);
          if (myUnbuiltNodes.contains(each)) {
            makeLoadingOrLeafIfNoChildren(each);
            hasUnbuiltChildren = true;
          }
        }

        if (hasUnbuiltChildren) {
          getUpdater().addSubtreeToUpdate(node);
        }
      } else {
        getBuilder().expandNodeChildren(node);
      }

      processSmartExpand(node, canSmartExpand(node, true));
      processNodeActionsIfReady(node);
    }

    public void treeCollapsed(TreeExpansionEvent e) {
      dropUpdaterStateIfExternalChange();

      final TreePath path = e.getPath();
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (!(node.getUserObject() instanceof NodeDescriptor)) return;


      TreePath pathToSelect = null;
      if (isSelectionInside(node)) {
        pathToSelect = new TreePath(node.getPath());
      }


      NodeDescriptor descriptor = getDescriptorFrom(node);
      if (getBuilder().isDisposeOnCollapsing(descriptor)) {
        runDone(new Runnable() {
          public void run() {
            if (isDisposed(node)) return;

            TreePath nodePath = new TreePath(node.getPath());
            if (myTree.isExpanded(nodePath)) return;

            removeChildren(node);
            makeLoadingOrLeafIfNoChildren(node);
          }
        });
        if (node.equals(getRootNode())) {
          if (myTree.isRootVisible()) {
            //todo kirillk to investigate -- should be done by standard selction move
            //addSelectionPath(new TreePath(getRootNode().getPath()), true, Condition.FALSE);
          }
        }
        else {
          myTreeModel.reload(node);
        }
      }

      if (pathToSelect != null && myTree.isSelectionEmpty()) {
        addSelectionPath(pathToSelect, true, Condition.FALSE);
      }
    }

    private void removeChildren(DefaultMutableTreeNode node) {
      EnumerationCopy copy = new EnumerationCopy(node.children());
      while (copy.hasMoreElements()) {
        disposeNode((DefaultMutableTreeNode)copy.nextElement());
      }
      node.removeAllChildren();
      myTreeModel.nodeStructureChanged(node);
    }

    private boolean isSelectionInside(DefaultMutableTreeNode parent) {
      TreePath path = new TreePath(myTreeModel.getPathToRoot(parent));
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return false;
      for (TreePath path1 : paths) {
        if (path.isDescendant(path1)) return true;
      }
      return false;
    }
  }

  public boolean isInStructure(@Nullable Object element) {
    Object eachParent = element;
    while (eachParent != null) {
      if (getTreeStructure().getRootElement().equals(eachParent)) return true;
      eachParent = getTreeStructure().getParentElement(eachParent);
    }

    return false;
  }

  interface NodeAction {
    void onReady(DefaultMutableTreeNode node);
  }

  public void setCanYield(final boolean canYield) {
    myCanYield = canYield;
  }

  public Collection<TreeUpdatePass> getYeildingPasses() {
    return myYeildingPasses;
  }

  public boolean isBuilt(Object element) {
    if (!myElementToNodeMap.containsKey(element)) return false;
    final Object node = myElementToNodeMap.get(element);
    return !myUnbuiltNodes.contains(node);
  }

  static class LoadedChildren {

    private List myElements;
    private Map<Object, NodeDescriptor> myDescriptors = new HashMap<Object, NodeDescriptor>();
    private Map<NodeDescriptor, Boolean> myChanges = new HashMap<NodeDescriptor, Boolean>();

    LoadedChildren(Object[] elements) {
      myElements = Arrays.asList(elements != null ? elements : new Object[0]);
    }

    void putDescriptor(Object element, NodeDescriptor descriptor, boolean isChanged) {
      assert myElements.contains(element);
      myDescriptors.put(element, descriptor);
      myChanges.put(descriptor, isChanged);
    }

    List getElements() {
      return myElements;
    }

    NodeDescriptor getDescriptor(Object element) {
      return myDescriptors.get(element);
    }

    @Override
    public String toString() {
      return Arrays.asList(myElements) + "->" + myChanges;
    }

    public boolean isUpdated(Object element) {
      NodeDescriptor desc = getDescriptor(element);
      return myChanges.get(desc);
    }
  }

  UpdaterTreeState getUpdaterState() {
    return myUpdaterState;
  }

  private ActionCallback addReadyCallback(Object requestor) {
    synchronized (myReadyCallbacks) {
      ActionCallback cb = myReadyCallbacks.get(requestor);
      if (cb == null) {
        cb = new ActionCallback();
        myReadyCallbacks.put(requestor, cb);
      }

      return cb;
    }
  }

  private ActionCallback[] getReadyCallbacks(boolean clear) {
    synchronized (myReadyCallbacks) {
      ActionCallback[] result = myReadyCallbacks.values().toArray(new ActionCallback[myReadyCallbacks.size()]);
      if (clear) {
        myReadyCallbacks.clear();
      }
      return result;
    }
  }

}
