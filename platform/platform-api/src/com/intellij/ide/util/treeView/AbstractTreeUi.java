// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.UiActivity;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.ide.util.treeView.TreeRunnable.TreeConsumer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.AlwaysExpandedTree;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.*;
import com.intellij.util.concurrency.LockToken;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * @deprecated use {@link com.intellij.ui.tree.AsyncTreeModel} and {@link com.intellij.ui.tree.StructureTreeModel} instead.
 */
@Deprecated(forRemoval = true)
public class AbstractTreeUi {
  private static final Logger LOG = Logger.getInstance(AbstractTreeBuilder.class);
  protected JTree myTree;// protected for TestNG

  private DefaultTreeModel myTreeModel;
  private AbstractTreeStructure myTreeStructure;
  private AbstractTreeUpdater myUpdater;

  private Comparator<? super NodeDescriptor<?>> myNodeDescriptorComparator;

  private final Comparator<TreeNode> myNodeComparator = new Comparator<>() {
    @Override
    public int compare(TreeNode n1, TreeNode n2) {
      if (isLoadingNode(n1) && isLoadingNode(n2)) return 0;
      if (isLoadingNode(n1)) return -1;
      if (isLoadingNode(n2)) return 1;

      NodeDescriptor<?> nodeDescriptor1 = getDescriptorFrom(n1);
      NodeDescriptor<?> nodeDescriptor2 = getDescriptorFrom(n2);

      if (nodeDescriptor1 == null && nodeDescriptor2 == null) return 0;
      if (nodeDescriptor1 == null) return -1;
      if (nodeDescriptor2 == null) return 1;

      return myNodeDescriptorComparator != null
             ? myNodeDescriptorComparator.compare(nodeDescriptor1, nodeDescriptor2)
             : Integer.compare(nodeDescriptor1.getIndex(), nodeDescriptor2.getIndex());
    }
  };

  long myOwnComparatorStamp;
  private long myLastComparatorStamp;

  private DefaultMutableTreeNode myRootNode;
  private final Map<Object, Object> myElementToNodeMap = new HashMap<>();
  private final Set<DefaultMutableTreeNode> myUnbuiltNodes = new HashSet<>();
  private TreeExpansionListener myExpansionListener;
  private MySelectionListener mySelectionListener;

  private final QueueProcessor<Runnable> myWorker = new QueueProcessor<>(runnable -> {
    runnable.run();
    TimeoutUtil.sleep(1);
  });
  private final Set<Runnable> myActiveWorkerTasks = new HashSet<>();

  private ProgressIndicator myProgress;
  private AbstractTreeNode<Object> TREE_NODE_WRAPPER;

  private boolean myRootNodeWasQueuedToInitialize;
  private boolean myRootNodeInitialized;

  private final Map<Object, List<NodeAction>> myNodeActions = new HashMap<>();
  private boolean myUpdateFromRootRequested;
  private boolean myWasEverShown;
  private boolean myUpdateIfInactive;

  private final Map<Object, UpdateInfo> myLoadedInBackground = new HashMap<>();
  private final Map<Object, List<NodeAction>> myNodeChildrenActions = new HashMap<>();

  private long myClearOnHideDelay = -1;
  private volatile long ourUi2Countdown;

  private final Set<Runnable> myDeferredSelections = new HashSet<>();
  private final Set<Runnable> myDeferredExpansions = new HashSet<>();

  private boolean myCanProcessDeferredSelections;

  private UpdaterTreeState myUpdaterState;
  private AbstractTreeBuilder myBuilder;

  private final Set<DefaultMutableTreeNode> myUpdatingChildren = new HashSet<>();

  private boolean myCanYield;

  private final List<TreeUpdatePass> myYieldingPasses = new ArrayList<>();

  private boolean myYieldingNow;

  private final Set<DefaultMutableTreeNode> myPendingNodeActions = new HashSet<>();
  private final Set<Runnable> myYieldingDoneRunnables = new HashSet<>();

  private final Alarm myBusyAlarm = new Alarm();
  private final Runnable myWaiterForReady = new TreeRunnable("AbstractTreeUi.myWaiterForReady") {
    @Override
    public void perform() {
      maybeSetBusyAndScheduleWaiterForReady(false, null);
    }
  };

  private final RegistryValue myYieldingUpdate = Registry.get("ide.tree.yieldingUiUpdate");
  private final RegistryValue myShowBusyIndicator = Registry.get("ide.tree.showBusyIndicator");
  private final RegistryValue myWaitForReadyTime = Registry.get("ide.tree.waitForReadyTimeout");

  private boolean myWasEverIndexNotReady;
  private boolean myShowing;
  private final FocusAdapter myFocusListener = new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
      maybeReady();
    }
  };
  private final Set<DefaultMutableTreeNode> myNotForSmartExpand = new HashSet<>();
  private TreePath myRequestedExpand;

  private TreePath mySilentExpand;
  private TreePath mySilentSelect;

  private final ActionCallback myInitialized = new ActionCallback();
  private final BusyObject.Impl myBusyObject = new BusyObject.Impl() {
    @Override
    public boolean isReady() {
      return AbstractTreeUi.this.isReady(true);
    }

    @Override
    protected void onReadyWasSent() {
      removeActivity();
    }
  };

  private boolean myPassThroughMode;

  private final Set<Object> myAutoExpandRoots = new HashSet<>();
  private final RegistryValue myAutoExpandDepth = Registry.get("ide.tree.autoExpandMaxDepth");

  private final Set<DefaultMutableTreeNode> myWillBeExpanded = new HashSet<>();
  private SimpleTimerTask myCleanupTask;

  private final AtomicBoolean myCancelRequest = new AtomicBoolean();
  private final ReentrantLock myStateLock = new ReentrantLock();

  private final AtomicBoolean myResettingToReadyNow = new AtomicBoolean();

  private final Map<Progressive, ProgressIndicator> myBatchIndicators = new HashMap<>();
  private final Map<Progressive, ActionCallback> myBatchCallbacks = new HashMap<>();

  private final Map<DefaultMutableTreeNode, DefaultMutableTreeNode> myCancelledBuild = new WeakHashMap<>();

  private boolean mySelectionIsAdjusted;
  private boolean myReleaseRequested;

  private boolean mySelectionIsBeingAdjusted;

  private final Set<Object> myRevalidatedObjects = new HashSet<>();

  private final Set<Runnable> myUserRunnables = new HashSet<>();

  private UiActivityMonitor myActivityMonitor;
  @NonNls private UiActivity myActivityId;

  @Override
  public String toString() {
    return "AbstractTreeUi: builder = " + myBuilder;
  }

  protected void init(@NotNull AbstractTreeBuilder builder,
                      @NotNull JTree tree,
                      @NotNull DefaultTreeModel treeModel,
                      AbstractTreeStructure treeStructure,
                      @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                      boolean updateIfInactive) {
    myBuilder = builder;
    myTree = tree;
    myTreeModel = treeModel;
    myActivityMonitor = UiActivityMonitor.getInstance();
    myActivityId = new UiActivity.AsyncBgOperation("TreeUi " + this);
    addModelListenerToDiagnoseAccessOutsideEdt();
    TREE_NODE_WRAPPER = AbstractTreeBuilder.createSearchingTreeNodeWrapper();
    myTree.setModel(myTreeModel);
    setRootNode((DefaultMutableTreeNode)treeModel.getRoot());
    myTreeStructure = treeStructure;
    myNodeDescriptorComparator = comparator;
    myUpdateIfInactive = updateIfInactive;

    UIUtil.invokeLaterIfNeeded(new TreeRunnable("AbstractTreeUi.init") {
      @Override
      public void perform() {
        if (!wasRootNodeInitialized()) {
          if (myRootNode.getChildCount() == 0) {
            insertLoadingNode(myRootNode, true);
          }
        }
      }
    });

    myExpansionListener = new MyExpansionListener();
    myTree.addTreeExpansionListener(myExpansionListener);

    mySelectionListener = new MySelectionListener();
    myTree.addTreeSelectionListener(mySelectionListener);

    setUpdater(getBuilder().createUpdater());
    myProgress = getBuilder().createProgressIndicator();
    Disposer.register(getBuilder(), getUpdater());
    if (myProgress != null) {
      Disposer.register(getBuilder(), () -> myProgress.cancel());
    }

    UiNotifyConnector uiNotify = UiNotifyConnector.installOn(tree, new Activatable() {
      @Override
      public void showNotify() {
        myShowing = true;
        myWasEverShown = true;
        if (canInitiateNewActivity()) {
          activate(true);
        }
      }

      @Override
      public void hideNotify() {
        myShowing = false;
        if (canInitiateNewActivity()) {
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

  private void maybeSetBusyAndScheduleWaiterForReady(boolean forcedBusy, @Nullable Object element) {
    if (!myShowBusyIndicator.asBoolean()) return;

    boolean canUpdateBusyState = false;

    if (forcedBusy) {
      if (canYield() || isToBuildChildrenInBackground(element)) {
        canUpdateBusyState = true;
      }
    } else {
      canUpdateBusyState = true;
    }

    if (!canUpdateBusyState) return;

    if (myTree instanceof Tree tree) {
      boolean isBusy = !isReady(true) || forcedBusy;
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

  private void setHoldSize(boolean holdSize) {
    if (myTree instanceof Tree tree) {
      tree.setHoldSize(holdSize);
    }
  }

  private void cleanUpAll() {
    long now = System.currentTimeMillis();
    long timeToCleanup = ourUi2Countdown;
    if (timeToCleanup != 0 && now >= timeToCleanup) {
      ourUi2Countdown = 0;
      Runnable runnable = new TreeRunnable("AbstractTreeUi.cleanUpAll") {
        @Override
        public void perform() {
          if (!canInitiateNewActivity()) return;

          myCleanupTask = null;
          getBuilder().cleanUp();
        }
      };
      if (isPassthroughMode()) {
        runnable.run();
      }
      else {
        invokeLaterIfNeeded(false, runnable);
      }
    }
  }

  void doCleanUp() {
    Runnable cleanup = new TreeRunnable("AbstractTreeUi.doCleanUp") {
      @Override
      public void perform() {
        if (canInitiateNewActivity()) {
          cleanUpNow();
        }
      }
    };

    if (isPassthroughMode()) {
      cleanup.run();
    }
    else {
      invokeLaterIfNeeded(false, cleanup);
    }
  }

  void invokeLaterIfNeeded(boolean forceEdt, @NotNull Runnable runnable) {
    Runnable actual = new TreeRunnable("AbstractTreeUi.invokeLaterIfNeeded") {
      @Override
      public void perform() {
        if (!isReleased()) {
          runnable.run();
        }
      }
    };

    if (isPassthroughMode() || !forceEdt && !isEdt() && !isTreeShowing() && !myWasEverShown) {
      actual.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(actual);
    }
  }

  public void activate(boolean byShowing) {
    cancelCurrentCleanupTask();

    myCanProcessDeferredSelections = true;
    ourUi2Countdown = 0;

    if (!myWasEverShown || myUpdateFromRootRequested || myUpdateIfInactive) {
      getBuilder().updateFromRoot();
    }

    getUpdater().showNotify();

    myWasEverShown |= byShowing;
  }

  private void cancelCurrentCleanupTask() {
    if (myCleanupTask != null) {
      myCleanupTask.cancel();
      myCleanupTask = null;
    }
  }

  void deactivate() {
    getUpdater().hideNotify();
    myBusyAlarm.cancelAllRequests();

    if (!myWasEverShown) return;
    // ask for termination of background children calculation
    if (myProgress != null && myProgress.isRunning()) myProgress.cancel();
    if (!isReady()) {
      cancelUpdate();
      myUpdateFromRootRequested = true;
    }

    if (getClearOnHideDelay() >= 0) {
      ourUi2Countdown = System.currentTimeMillis() + getClearOnHideDelay();
      scheduleCleanUpAll();
    }
  }

  private void scheduleCleanUpAll() {
    cancelCurrentCleanupTask();

    myCleanupTask = SimpleTimer.getInstance().setUp(new TreeRunnable("AbstractTreeUi.scheduleCleanUpAll") {
      @Override
      public void perform() {
        cleanUpAll();
      }
    }, getClearOnHideDelay());
  }

  void requestRelease() {
    myReleaseRequested = true;
    cancelUpdate().doWhenDone(new TreeRunnable("AbstractTreeUi.requestRelease: on done") {
      @Override
      public void perform() {
        releaseNow();
      }
    });
  }

  public ProgressIndicator getProgress() {
    return myProgress;
  }

  private void releaseNow() {
    try (LockToken ignored = acquireLock()) {
      myTree.removeTreeExpansionListener(myExpansionListener);
      myTree.removeTreeSelectionListener(mySelectionListener);
      myTree.removeFocusListener(myFocusListener);

      disposeNode(getRootNode());
      myElementToNodeMap.clear();
      getUpdater().cancelAllRequests();
      myWorker.clear();
      clearWorkerTasks();
      TREE_NODE_WRAPPER.setValue(null);
      if (myProgress != null) {
        myProgress.cancel();
      }

      cancelCurrentCleanupTask();

      myTree = null;
      setUpdater(null);
      myTreeStructure = null;
      myBuilder.releaseUi();
      myBuilder = null;

      clearNodeActions();

      myDeferredSelections.clear();
      myDeferredExpansions.clear();
      myYieldingDoneRunnables.clear();
    }
  }

  public boolean isReleased() {
    return myBuilder == null;
  }

  void doExpandNodeChildren(@NotNull DefaultMutableTreeNode node) {
    if (!myUnbuiltNodes.contains(node)) return;
    if (isLoadedInBackground(getElementFor(node))) return;

    AbstractTreeStructure structure = getTreeStructure();
    structure.asyncCommit().doWhenDone(new TreeRunnable("AbstractTreeUi.doExpandNodeChildren") {
      @Override
      public void perform() {
        addSubtreeToUpdate(node);
        // at this point some tree updates may already have been run as a result of
        // in tests these updates may lead to the instance disposal, so getUpdater() at the next line may return null
        AbstractTreeUpdater updater = getUpdater();
        if (updater != null) {
          updater.performUpdate();
        }
      }
    });
    //if (structure.hasSomethingToCommit()) structure.commit();
  }

  public final AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  public final JTree getTree() {
    return myTree;
  }

  @Nullable
  private static NodeDescriptor<?> getDescriptorFrom(Object node) {
    if (node instanceof DefaultMutableTreeNode) {
      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof NodeDescriptor) {
        return (NodeDescriptor<?>)userObject;
      }
    }

    return null;
  }

  @Nullable
  public final DefaultMutableTreeNode getNodeForElement(@NotNull Object element, boolean validateAgainstStructure) {
    DefaultMutableTreeNode result = null;
    if (validateAgainstStructure) {
      int index = 0;
      while (true) {
        DefaultMutableTreeNode node = findNode(element, index);
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

  private boolean isNodeInStructure(@NotNull DefaultMutableTreeNode node) {
    return TreeUtil.isAncestor(getRootNode(), node) && getRootNode() == myTreeModel.getRoot();
  }

  private boolean isNodeValidForElement(@NotNull Object element, @NotNull DefaultMutableTreeNode node) {
    return isSameHierarchy(element, node) || isValidChildOfParent(element, node);
  }

  private boolean isValidChildOfParent(@NotNull Object element, @NotNull DefaultMutableTreeNode node) {
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
    Object parentElement = getElementFor(parent);
    if (!isInStructure(parentElement)) return false;

    if (parent instanceof ElementNode) {
      return ((ElementNode)parent).isValidChild(element);
    }
    for (int i = 0; i < parent.getChildCount(); i++) {
      TreeNode child = parent.getChildAt(i);
      Object eachElement = getElementFor(child);
      if (element.equals(eachElement)) return true;
    }

    return false;
  }

  private boolean isSameHierarchy(@NotNull  Object element, @NotNull DefaultMutableTreeNode node) {
    Object eachParent = element;
    DefaultMutableTreeNode eachParentNode = node;
    boolean valid;
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

  @Nullable
  public final DefaultMutableTreeNode getNodeForPath(Object @NotNull [] path) {
    DefaultMutableTreeNode node = null;
    for (Object pathElement : path) {
      node = node == null ? getFirstNode(pathElement) : findNodeForChildElement(node, pathElement);
      if (node == null) {
        break;
      }
    }
    return node;
  }

  final void buildNodeForElement(@NotNull Object element) {
    getUpdater().performUpdate();
    DefaultMutableTreeNode node = getNodeForElement(element, false);
    if (node == null) {
      List<Object> elements = new ArrayList<>();
      while (true) {
        element = getTreeStructure().getParentElement(element);
        if (element == null) {
          break;
        }
        elements.add(0, element);
      }

      for (Object element1 : elements) {
        node = getNodeForElement(element1, false);
        if (node != null) {
          expand(node, true);
        }
      }
    }
  }

  public final void buildNodeForPath(Object @NotNull [] path) {
    getUpdater().performUpdate();
    DefaultMutableTreeNode node = null;
    for (Object pathElement : path) {
      node = node == null ? getFirstNode(pathElement) : findNodeForChildElement(node, pathElement);
      if (node != null && node != path[path.length - 1]) {
        expand(node, true);
      }
    }
  }

  public final void setNodeDescriptorComparator(Comparator<? super NodeDescriptor<?>> nodeDescriptorComparator) {
    myNodeDescriptorComparator = nodeDescriptorComparator;
    myLastComparatorStamp = -1;
    getBuilder().queueUpdateFrom(getTreeStructure().getRootElement(), true);
  }

  @NotNull
  protected AbstractTreeBuilder getBuilder() {
    return myBuilder;
  }

  protected final void initRootNode() {
    if (myUpdateIfInactive) {
      activate(false);
    }
    else {
      myUpdateFromRootRequested = true;
    }
  }

  private boolean initRootNodeNowIfNeeded(@NotNull TreeUpdatePass pass) {
    boolean wasCleanedUp = false;
    if (myRootNodeWasQueuedToInitialize) {
      Object root = getTreeStructure().getRootElement();

      Object currentRoot = getElementFor(myRootNode);

      if (Comparing.equal(root, currentRoot)) return false;

      Object rootAgain = getTreeStructure().getRootElement();
      if (root != rootAgain && !root.equals(rootAgain)) {
        assert false : "getRootElement() if called twice must return either root1 == root2 or root1.equals(root2)";
      }

      cleanUpNow();
      wasCleanedUp = true;
    }

    if (myRootNodeWasQueuedToInitialize) return true;

    myRootNodeWasQueuedToInitialize = true;

    Object rootElement = getTreeStructure().getRootElement();
    addNodeAction(rootElement, false, node -> processDeferredActions());


    Ref<NodeDescriptor<?>> rootDescriptor = new Ref<>(null);
    boolean bgLoading = isToBuildChildrenInBackground(rootElement);

    Runnable build = new TreeRunnable("AbstractTreeUi.initRootNodeNowIfNeeded: build") {
      @Override
      public void perform() {
        rootDescriptor.set(getTreeStructure().createDescriptor(rootElement, null));
        getRootNode().setUserObject(rootDescriptor.get());
        update(rootDescriptor.get(), true);
        pass.addToUpdated(rootDescriptor.get());
      }
    };


    Runnable update = new TreeRunnable("AbstractTreeUi.initRootNodeNowIfNeeded: update") {
      @Override
      public void perform() {
        Object fromDescriptor = getElementFromDescriptor(rootDescriptor.get());
        if (!isNodeNull(fromDescriptor)) {
          createMapping(fromDescriptor, getRootNode());
        }


        insertLoadingNode(getRootNode(), true);

        boolean willUpdate = false;
        if (!rootDescriptor.isNull() && isAutoExpand(rootDescriptor.get())) {
          willUpdate = myUnbuiltNodes.contains(getRootNode());
          expand(getRootNode(), true);
        }
        ActionCallback callback;
        if (willUpdate) {
          callback = ActionCallback.DONE;
        }
        else {
          callback = updateNodeChildren(getRootNode(), pass, null, false, false, false, true, true);
        }
        callback.doWhenDone(new TreeRunnable("AbstractTreeUi.initRootNodeNowIfNeeded: on done updateNodeChildren") {
          @Override
          public void perform() {
            if (getRootNode().getChildCount() == 0) {
              myTreeModel.nodeChanged(getRootNode());
            }
          }
        });
      }
    };

    if (bgLoading) {
      queueToBackground(build, update)
        .onSuccess(new TreeConsumer<>("AbstractTreeUi.initRootNodeNowIfNeeded: on processed queueToBackground") {
          @Override
          public void perform() {
            invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.initRootNodeNowIfNeeded: on processed queueToBackground later") {
              @Override
              public void perform() {
                myRootNodeInitialized = true;
                processNodeActionsIfReady(myRootNode);
              }
            });
          }
        });
    }
    else {
      build.run();
      update.run();
      myRootNodeInitialized = true;
      processNodeActionsIfReady(myRootNode);
    }

    return wasCleanedUp;
  }

  private boolean isAutoExpand(@NotNull NodeDescriptor<?> descriptor) {
    return isAutoExpand(descriptor, true);
  }

  private boolean isAutoExpand(@NotNull NodeDescriptor<?> descriptor, boolean validate) {
    if (isAlwaysExpandedTree()) return false;

    boolean autoExpand = getBuilder().isAutoExpandNode(descriptor);

    Object element = getElementFromDescriptor(descriptor);
    if (validate && element != null) {
      autoExpand = validateAutoExpand(autoExpand, element);
    }

    if (!autoExpand && !myTree.isRootVisible()) {
      if (element != null && element.equals(getTreeStructure().getRootElement())) return true;
    }

    return autoExpand;
  }

  private boolean validateAutoExpand(boolean autoExpand, @NotNull Object element) {
    if (autoExpand) {
      int distance = getDistanceToAutoExpandRoot(element);
      if (distance < 0) {
        myAutoExpandRoots.add(element);
      }
      else {
        if (distance >= myAutoExpandDepth.asInteger() - 1) {
          autoExpand = false;
        }
      }

      if (autoExpand) {
        DefaultMutableTreeNode node = getNodeForElement(element, false);
        autoExpand = node != null && isInVisibleAutoExpandChain(node);
      }
    }
    return autoExpand;
  }

  private boolean isInVisibleAutoExpandChain(@NotNull DefaultMutableTreeNode child) {
    TreeNode eachParent = child;
    while (eachParent != null) {

      if (myRootNode == eachParent) return true;

      NodeDescriptor<?> eachDescriptor = getDescriptorFrom(eachParent);
      if (eachDescriptor == null || !isAutoExpand(eachDescriptor, false)) {
        TreePath path = getPathFor(eachParent);
        return myWillBeExpanded.contains(path.getLastPathComponent()) || myTree.isExpanded(path) && myTree.isVisible(path);
      }
      eachParent = eachParent.getParent();
    }

    return false;
  }

  private int getDistanceToAutoExpandRoot(@NotNull Object element) {
    int distance = 0;

    Object eachParent = element;
    while (eachParent != null) {
      if (myAutoExpandRoots.contains(eachParent)) break;
      eachParent = getTreeStructure().getParentElement(eachParent);
      distance++;
    }

    return eachParent != null ? distance : -1;
  }

  private boolean isAutoExpand(@NotNull DefaultMutableTreeNode node) {
    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    return descriptor != null && isAutoExpand(descriptor);
  }

  private boolean isAlwaysExpandedTree() {
    return myTree instanceof AlwaysExpandedTree && ((AlwaysExpandedTree)myTree).isAlwaysExpanded();
  }

  @NotNull
  private Promise<Boolean> update(@NotNull NodeDescriptor<?> nodeDescriptor, boolean now) {
    Promise<Boolean> promise;
    if (now || isPassthroughMode()) {
      promise = Promises.resolvedPromise(update(nodeDescriptor));
    }
    else {
      AsyncPromise<Boolean> result = new AsyncPromise<>();
      promise = result;

      boolean bgLoading = isToBuildInBackground(nodeDescriptor);

      boolean edt = isEdt();
      if (bgLoading) {
        if (edt) {
          AtomicBoolean changes = new AtomicBoolean();
          queueToBackground(new TreeRunnable("AbstractTreeUi.update: build") {
                              @Override
                              public void perform() {
                                changes.set(update(nodeDescriptor));
                              }
                            }, new TreeRunnable("AbstractTreeUi.update: post") {
                              @Override
                              public void perform() {
                                result.setResult(changes.get());
                              }
                            }
          );
        }
        else {
          result.setResult(update(nodeDescriptor));
        }
      }
      else {
        if (edt || !myWasEverShown) {
          result.setResult(update(nodeDescriptor));
        }
        else {
          invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.update: later") {
            @Override
            public void perform() {
              execute(new TreeRunnable("AbstractTreeUi.update: later execute") {
                @Override
                public void perform() {
                  result.setResult(update(nodeDescriptor));
                }
              });
            }
          });
        }
      }
    }

    promise.onSuccess(changes -> {
      if (!changes) {
        return;
      }

      invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.update: on done result") {
        @Override
        public void perform() {
          Object element = nodeDescriptor.getElement();
          DefaultMutableTreeNode node = element == null ? null : getNodeForElement(element, false);
          if (node != null) {
            TreePath path = getPathFor(node);
            if (myTree.isVisible(path)) {
              updateNodeImageAndPosition(node);
            }
          }
        }
      });
    });
    return promise;
  }

  private boolean update(@NotNull NodeDescriptor<?> nodeDescriptor) {
    while(true) {
      try (LockToken ignored = attemptLock()) {
        if (ignored == null) {  // async children calculation is in progress under lock
          if (myProgress != null && myProgress.isRunning()) myProgress.cancel();
          continue;
        }
        AtomicBoolean update = new AtomicBoolean();
        execute(new TreeRunnable("AbstractTreeUi.update") {
          @Override
          public void perform() {
            nodeDescriptor.setUpdateCount(nodeDescriptor.getUpdateCount() + 1);
            update.set(getBuilder().updateNodeDescriptor(nodeDescriptor));
          }
        });
        return update.get();
      }
      catch (IndexNotReadyException e) {
        warnOnIndexNotReady(e);
        return false;
      }
      catch (InterruptedException e) {
        LOG.info(e);
        return false;
      }
    }
  }

  public void assertIsDispatchThread() {
    if (isPassthroughMode()) return;

    if ((isTreeShowing() || myWasEverShown) && !isEdt()) {
      LOG.error("Must be in event-dispatch thread");
    }
  }

  private static boolean isEdt() {
    return SwingUtilities.isEventDispatchThread();
  }

  private boolean isTreeShowing() {
    return myShowing;
  }

  private void assertNotDispatchThread() {
    if (isPassthroughMode()) return;

    if (isEdt()) {
      LOG.error("Must not be in event-dispatch thread");
    }
  }

  private void processDeferredActions() {
    processDeferredActions(myDeferredSelections);
    processDeferredActions(myDeferredExpansions);
  }

  private static void processDeferredActions(@NotNull Set<Runnable> actions) {
    Runnable[] runnables = actions.toArray(ArrayUtil.EMPTY_RUNNABLE_ARRAY);
    actions.clear();
    for (Runnable runnable : runnables) {
      runnable.run();
    }
  }

  //todo: to make real callback
  @NotNull
  public ActionCallback queueUpdate(Object element) {
    return queueUpdate(element, true);
  }

  @NotNull
  public ActionCallback queueUpdate(Object fromElement, boolean updateStructure) {
    assertIsDispatchThread();

    try {
      if (getUpdater() == null) {
        return ActionCallback.REJECTED;
      }

      ActionCallback result = new ActionCallback();
      DefaultMutableTreeNode nodeToUpdate = null;
      boolean updateElementStructure = updateStructure;
      for (Object element = fromElement; element != null; element = getTreeStructure().getParentElement(element)) {
        DefaultMutableTreeNode node = getFirstNode(element);
        if (node != null) {
          nodeToUpdate = node;
          break;
        }
        updateElementStructure = true; // always update children if element does not exist
      }

      addSubtreeToUpdate(nodeToUpdate != null? nodeToUpdate : getRootNode(), new TreeRunnable("AbstractTreeUi.queueUpdate") {
        @Override
        public void perform() {
          result.setDone();
        }
      }, updateElementStructure);

      return result;
    }
    catch (ProcessCanceledException e) {
      return ActionCallback.REJECTED;
    }
  }

  public void doUpdateFromRoot() {
    updateSubtree(getRootNode(), false);
  }

  public final void updateSubtree(@NotNull DefaultMutableTreeNode node, boolean canSmartExpand) {
    updateSubtree(new TreeUpdatePass(node), canSmartExpand);
  }

  private void updateSubtree(@NotNull TreeUpdatePass pass, boolean canSmartExpand) {
    AbstractTreeUpdater updater = getUpdater();
    if (updater != null) {
      updater.addSubtreeToUpdate(pass);
    }
    else {
      updateSubtreeNow(pass, canSmartExpand);
    }
  }

  final void updateSubtreeNow(@NotNull TreeUpdatePass pass, boolean canSmartExpand) {
    maybeSetBusyAndScheduleWaiterForReady(true, getElementFor(pass.getNode()));
    setHoldSize(true);

    boolean consumed = initRootNodeNowIfNeeded(pass);
    if (consumed) return;

    DefaultMutableTreeNode node = pass.getNode();

    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;

    if (pass.isUpdateStructure()) {
      setUpdaterState(new UpdaterTreeState(this)).beforeSubtreeUpdate();

      boolean forceUpdate = true;
      TreePath path = getPathFor(node);
      boolean invisible = !myTree.isExpanded(path) && (path.getParentPath() == null || !myTree.isExpanded(path.getParentPath()));

      if (invisible && myUnbuiltNodes.contains(node)) {
        forceUpdate = false;
      }

      updateNodeChildren(node, pass, null, false, canSmartExpand, forceUpdate, false, pass.isUpdateChildren());
    }
    else {
      updateRow(0, pass);
    }
  }

  private void updateRow(int row, @NotNull TreeUpdatePass pass) {
    LOG.debug("updateRow: ", row, " - ", pass);
    invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.updateRow") {
      @Override
      public void perform() {
        if (row >= getTree().getRowCount()) return;

        TreePath path = getTree().getPathForRow(row);
        if (path != null) {
          NodeDescriptor<?> descriptor = getDescriptorFrom(path.getLastPathComponent());
          if (descriptor != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            maybeYield(() -> update(descriptor, false)
              .onSuccess(new TreeConsumer<>("AbstractTreeUi.updateRow: inner") {
                @Override
                public void perform() {
                  updateRow(row + 1, pass);
                }
              }), pass, node);
          }
        }
      }
    });
  }

  boolean isToBuildChildrenInBackground(Object element) {
    AbstractTreeStructure structure = getTreeStructure();
    return element != null && structure.isToBuildChildrenInBackground(element);
  }

  private boolean isToBuildInBackground(NodeDescriptor<?> descriptor) {
    return isToBuildChildrenInBackground(getElementFromDescriptor(descriptor));
  }

  @NotNull
  private UpdaterTreeState setUpdaterState(@NotNull UpdaterTreeState state) {
    if (state.equals(myUpdaterState)) return state;

    UpdaterTreeState oldState = myUpdaterState;
    if (oldState == null) {
      myUpdaterState = state;
      return state;
    }
    else {
      oldState.addAll(state);
      return oldState;
    }
  }

  void doUpdateNode(@NotNull DefaultMutableTreeNode node) {
    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;
    Object prevElement = getElementFromDescriptor(descriptor);
    if (prevElement == null) return;
    update(descriptor, false)
      .onSuccess(changes -> {
        if (!isValid(descriptor)) {
          if (isInStructure(prevElement)) {
            Object toUpdate = ObjectUtils.notNull(getTreeStructure().getParentElement(prevElement), getTreeStructure().getRootElement());
            getUpdater().addSubtreeToUpdateByElement(toUpdate);
            return;
          }
        }
        if (changes) {
          updateNodeImageAndPosition(node);
        }
      });
  }

  public Object getElementFromDescriptor(NodeDescriptor<?> descriptor) {
    return getBuilder().getTreeStructureElement(descriptor);
  }

  @NotNull
  private ActionCallback updateNodeChildren(@NotNull DefaultMutableTreeNode node,
                                            @NotNull TreeUpdatePass pass,
                                            @Nullable LoadedChildren loadedChildren,
                                            boolean forcedNow,
                                            boolean toSmartExpand,
                                            boolean forceUpdate,
                                            boolean descriptorIsUpToDate,
                                            boolean updateChildren) {
    AbstractTreeStructure treeStructure = getTreeStructure();
    ActionCallback result = treeStructure.asyncCommit();
    result.doWhenDone(new TreeRunnable("AbstractTreeUi.updateNodeChildren: on done") {
      @Override
      public void perform() {
        try {
          removeFromCancelled(node);
          execute(new TreeRunnable("AbstractTreeUi.updateNodeChildren: execute") {
            @Override
            public void perform() {
              doUpdateChildren(node, pass, loadedChildren, forcedNow, toSmartExpand, forceUpdate, descriptorIsUpToDate, updateChildren);
            }
          });
        }
        catch (ProcessCanceledException e) {
          addToCancelled(node);
          throw e;
        }
      }
    });

    return result;
  }

  private void doUpdateChildren(@NotNull DefaultMutableTreeNode node,
                                @NotNull TreeUpdatePass pass,
                                @Nullable LoadedChildren loadedChildren,
                                boolean forcedNow,
                                boolean toSmartExpand,
                                boolean forceUpdate,
                                boolean descriptorIsUpToDate,
                                boolean updateChildren) {
    try {

      NodeDescriptor<?> descriptor = getDescriptorFrom(node);
      if (descriptor == null) {
        removeFromUnbuilt(node);
        removeLoading(node, true);
        return;
      }

      boolean descriptorIsReady = descriptorIsUpToDate || pass.isUpdated(descriptor);

      boolean wasExpanded = myTree.isExpanded(new TreePath(node.getPath())) || isAutoExpand(node);
      boolean wasLeaf = node.getChildCount() == 0;


      boolean bgBuild = isToBuildInBackground(descriptor);
      boolean requiredToUpdateChildren = forcedNow || wasExpanded;

      if (!requiredToUpdateChildren && forceUpdate) {
        boolean alwaysPlus = getBuilder().isAlwaysShowPlus(descriptor);
        if (alwaysPlus && wasLeaf) {
          requiredToUpdateChildren = true;
        }
        else {
          requiredToUpdateChildren = !alwaysPlus;
          if (!requiredToUpdateChildren && !myUnbuiltNodes.contains(node)) {
            removeChildren(node);
          }
        }
      }

      AtomicReference<LoadedChildren> preloaded = new AtomicReference<>(loadedChildren);

      if (!requiredToUpdateChildren) {
        if (myUnbuiltNodes.contains(node) && node.getChildCount() == 0) {
          insertLoadingNode(node, true);
        }

        if (!descriptorIsReady) {
          update(descriptor, false);
        }

        return;
      }

      if (!forcedNow && !bgBuild && myUnbuiltNodes.contains(node)) {
        if (!descriptorIsReady) {
          update(descriptor, true);
          descriptorIsReady = true;
        }

        if (processAlwaysLeaf(node) || !updateChildren) {
          return;
        }

        Pair<Boolean, LoadedChildren> unbuilt = processUnbuilt(node, descriptor, pass, wasExpanded, null);

        if (unbuilt.getFirst()) {
          return;
        }
        preloaded.set(unbuilt.getSecond());
      }


      boolean childForceUpdate = isChildNodeForceUpdate(node, forceUpdate, wasExpanded);

      if (!forcedNow && isToBuildInBackground(descriptor)) {
        boolean alwaysLeaf = processAlwaysLeaf(node);
        queueBackgroundUpdate(
          new UpdateInfo(descriptor, pass, canSmartExpand(node, toSmartExpand), wasExpanded, childForceUpdate, descriptorIsReady,
                         !alwaysLeaf && updateChildren), node);
      }
      else {
        if (!descriptorIsReady) {
          update(descriptor, false)
            .onSuccess(new TreeConsumer<>("AbstractTreeUi.doUpdateChildren") {
              @Override
              public void perform() {
                if (processAlwaysLeaf(node) || !updateChildren) return;
                updateNodeChildrenNow(node, pass, preloaded.get(), toSmartExpand, wasExpanded, childForceUpdate);
              }
            });
        }
        else {
          if (processAlwaysLeaf(node) || !updateChildren) return;

          updateNodeChildrenNow(node, pass, preloaded.get(), toSmartExpand, wasExpanded, childForceUpdate);
        }
      }
    }
    finally {
      if (!isReleased()) {
        processNodeActionsIfReady(node);
      }
    }
  }

  private boolean processAlwaysLeaf(@NotNull DefaultMutableTreeNode node) {
    Object element = getElementFor(node);
    NodeDescriptor<?> desc = getDescriptorFrom(node);

    if (desc == null) return false;

    if (element != null && getTreeStructure().isAlwaysLeaf(element)) {
      removeFromUnbuilt(node);
      removeLoading(node, true);

      if (node.getChildCount() > 0) {
        TreeNode[] children = new TreeNode[node.getChildCount()];
        for (int i = 0; i < node.getChildCount(); i++) {
          children[i] = node.getChildAt(i);
        }

        if (isSelectionInside(node)) {
          addSelectionPath(getPathFor(node), true, Conditions.alwaysTrue(), null);
        }

        processInnerChange(new TreeRunnable("AbstractTreeUi.processAlwaysLeaf") {
          @Override
          public void perform() {
            for (TreeNode each : children) {
              removeNodeFromParent((MutableTreeNode)each, true);
              disposeNode((DefaultMutableTreeNode)each);
            }
          }
        });
      }

      removeFromUnbuilt(node);
      desc.setWasDeclaredAlwaysLeaf(true);
      processNodeActionsIfReady(node);
      return true;
    }
    else {
      boolean wasLeaf = desc.isWasDeclaredAlwaysLeaf();
      desc.setWasDeclaredAlwaysLeaf(false);

      if (wasLeaf) {
        insertLoadingNode(node, true);
      }

      return false;
    }
  }

  private boolean isChildNodeForceUpdate(@NotNull DefaultMutableTreeNode node, boolean parentForceUpdate, boolean parentExpanded) {
    TreePath path = getPathFor(node);
    return parentForceUpdate && (parentExpanded || myTree.isExpanded(path));
  }

  private void updateNodeChildrenNow(@NotNull DefaultMutableTreeNode node,
                                     @NotNull TreeUpdatePass pass,
                                     @Nullable LoadedChildren preloadedChildren,
                                     boolean toSmartExpand,
                                     boolean wasExpanded,
                                     boolean forceUpdate) {
    if (isUpdatingChildrenNow(node)) return;

    if (!canInitiateNewActivity()) {
      throw new ProcessCanceledException();
    }

    NodeDescriptor<?> descriptor = getDescriptorFrom(node);

    MutualMap<Object, Integer> elementToIndexMap = loadElementsFromStructure(descriptor, preloadedChildren);
    LoadedChildren loadedChildren =
      preloadedChildren != null ? preloadedChildren : new LoadedChildren(elementToIndexMap.getKeys().toArray());


    addToUpdatingChildren(node);
    pass.setCurrentNode(node);

    boolean canSmartExpand = canSmartExpand(node, toSmartExpand);

    removeFromUnbuilt(node);

    //noinspection unchecked
    processExistingNodes(node, elementToIndexMap, pass, canSmartExpand(node, toSmartExpand), forceUpdate, wasExpanded, preloadedChildren)
      .onSuccess(new TreeConsumer("AbstractTreeUi.updateNodeChildrenNow: on done processExistingNodes") {
        @Override
        public void perform() {
          if (isDisposed(node)) {
            removeFromUpdatingChildren(node);
            return;
          }

          removeLoading(node, false);

          boolean expanded = isExpanded(node, wasExpanded);

          if (expanded) {
            myWillBeExpanded.add(node);
          }
          else {
            myWillBeExpanded.remove(node);
          }

          collectNodesToInsert(descriptor, elementToIndexMap, node, expanded, loadedChildren)
            .doWhenDone((Consumer<List<TreeNode>>)nodesToInsert -> {
              insertNodesInto(nodesToInsert, node);
              ActionCallback callback = updateNodesToInsert(nodesToInsert, pass, canSmartExpand, isChildNodeForceUpdate(node, forceUpdate, expanded));
              callback.doWhenDone(new TreeRunnable("AbstractTreeUi.updateNodeChildrenNow: on done updateNodesToInsert") {
                @Override
                public void perform() {
                  removeLoading(node, false);
                  removeFromUpdatingChildren(node);

                  if (node.getChildCount() > 0) {
                    if (expanded) {
                      expand(node, canSmartExpand);
                    }
                  }

                  if (!canInitiateNewActivity()) {
                    throw new ProcessCanceledException();
                  }

                  Object element = getElementFor(node);
                  addNodeAction(element, false, node1 -> removeLoading(node1, false));

                  processNodeActionsIfReady(node);
                }
              });
            }).doWhenProcessed(new TreeRunnable("AbstractTreeUi.updateNodeChildrenNow: on processed collectNodesToInsert") {
            @Override
            public void perform() {
              myWillBeExpanded.remove(node);
              removeFromUpdatingChildren(node);
              processNodeActionsIfReady(node);
            }
          });
        }
      })
      .onError(new TreeConsumer<>("AbstractTreeUi.updateNodeChildrenNow: on reject processExistingNodes") {
        @Override
        public void perform() {
          removeFromUpdatingChildren(node);
          processNodeActionsIfReady(node);
        }
      });
  }

  private boolean isDisposed(@NotNull DefaultMutableTreeNode node) {
    return !node.isNodeAncestor((DefaultMutableTreeNode)myTree.getModel().getRoot());
  }

  private void expandSilently(TreePath path) {
    assertIsDispatchThread();

    try {
      mySilentExpand = path;
      getTree().expandPath(path);
    }
    finally {
      mySilentExpand = null;
    }
  }

  private void addSelectionSilently(TreePath path) {
    assertIsDispatchThread();

    try {
      mySilentSelect = path;
      getTree().getSelectionModel().addSelectionPath(path);
    }
    finally {
      mySilentSelect = null;
    }
  }

  private void expand(@NotNull DefaultMutableTreeNode node, boolean canSmartExpand) {
    expand(new TreePath(node.getPath()), canSmartExpand);
  }

  private void expand(@NotNull TreePath path, boolean canSmartExpand) {
    Object last = path.getLastPathComponent();
    boolean isLeaf = myTree.getModel().isLeaf(path.getLastPathComponent());
    boolean isRoot = last == myTree.getModel().getRoot();
    TreePath parent = path.getParentPath();
    if (isRoot && !myTree.isExpanded(path)) {
      if (myTree.isRootVisible() || myUnbuiltNodes.contains(last)) {
        insertLoadingNode((DefaultMutableTreeNode)last, false);
      }
      expandPath(path, canSmartExpand);
    }
    else if (myTree.isExpanded(path) ||
             isLeaf && parent != null && myTree.isExpanded(parent) && !myUnbuiltNodes.contains(last) && !isCancelled(last)) {
      if (last instanceof DefaultMutableTreeNode) {
        processNodeActionsIfReady((DefaultMutableTreeNode)last);
      }
    }
    else {
      if (isLeaf && (myUnbuiltNodes.contains(last) || isCancelled(last))) {
        insertLoadingNode((DefaultMutableTreeNode)last, true);
        expandPath(path, canSmartExpand);
      }
      else if (isLeaf && parent != null) {
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)parent.getLastPathComponent();
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

  private Pair<Boolean, LoadedChildren> processUnbuilt(@NotNull DefaultMutableTreeNode node,
                                                       NodeDescriptor<?> descriptor,
                                                       @NotNull TreeUpdatePass pass,
                                                       boolean isExpanded,
                                                       @Nullable LoadedChildren loadedChildren) {
    Ref<Pair<Boolean, LoadedChildren>> result = new Ref<>();

    execute(new TreeRunnable("AbstractTreeUi.processUnbuilt") {
      @Override
      public void perform() {
        if (!isExpanded && getBuilder().isAlwaysShowPlus(descriptor)) {
          result.set(new Pair<>(true, null));
          return;
        }

        Object element = getElementFor(node);
        if (element == null) {
          trace("null element for node " + node);
          result.set(new Pair<>(true, null));
          return;
        }

        addToUpdatingChildren(node);

        try {
          LoadedChildren children = loadedChildren != null ? loadedChildren : new LoadedChildren(getChildrenFor(element));

          boolean processed;

          if (children.getElements().isEmpty()) {
            removeFromUnbuilt(node);
            removeLoading(node, true);
            processed = true;
          }
          else {
            if (isAutoExpand(node)) {
              addNodeAction(getElementFor(node), false, node1 -> {
                TreePath path = new TreePath(node1.getPath());
                if (getTree().isExpanded(path) || children.getElements().isEmpty()) {
                  removeLoading(node1, false);
                }
                else {
                  maybeYield(() -> {
                    expand(element, null);
                    return Promises.resolvedPromise();
                  }, pass, node1);
                }
              });
            }
            processed = false;
          }

          removeFromUpdatingChildren(node);

          processNodeActionsIfReady(node);

          result.set(new Pair<>(processed, children));
        }
        finally {
          removeFromUpdatingChildren(node);
        }
      }
    });

    return result.get();
  }

  private boolean removeIfLoading(@NotNull TreeNode node) {
    if (isLoadingNode(node)) {
      moveSelectionToParentIfNeeded(node);
      removeNodeFromParent((MutableTreeNode)node, false);
      return true;
    }

    return false;
  }

  private void moveSelectionToParentIfNeeded(@NotNull TreeNode node) {
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
  private Object[] getChildrenFor(Object element) {
    Ref<Object[]> passOne = new Ref<>();
    try (LockToken ignored = acquireLock()) {
      execute(new TreeRunnable("AbstractTreeUi.getChildrenFor") {
        @Override
        public void perform() {
          passOne.set(getTreeStructure().getChildElements(element));
        }
      });
    }
    catch (IndexNotReadyException e) {
      warnOnIndexNotReady(e);
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    if (!Registry.is("ide.tree.checkStructure")) return passOne.get();

    Object[] passTwo = getTreeStructure().getChildElements(element);

    Set<Object> two = ContainerUtil.newHashSet(passTwo);

    if (passOne.get().length != passTwo.length) {
      LOG.error(
        "AbstractTreeStructure.getChildren() must either provide same objects or new objects but with correct hashCode() and equals() methods. Wrong parent element=" +
        element);
    }
    else {
      for (Object eachInOne : passOne.get()) {
        if (!two.contains(eachInOne)) {
          LOG.error(
            "AbstractTreeStructure.getChildren() must either provide same objects or new objects but with correct hashCode() and equals() methods. Wrong parent element=" +
            element);
          break;
        }
      }
    }

    return passOne.get();
  }

  private void warnOnIndexNotReady(IndexNotReadyException e) {
    if (!myWasEverIndexNotReady) {
      myWasEverIndexNotReady = true;
      LOG.error("Tree is not dumb-mode-aware; treeBuilder=" + getBuilder() + " treeStructure=" + getTreeStructure(), e);
    }
  }

  @NotNull
  private ActionCallback updateNodesToInsert(@NotNull List<? extends TreeNode> nodesToInsert,
                                             @NotNull TreeUpdatePass pass,
                                             boolean canSmartExpand,
                                             boolean forceUpdate) {
    ActionCallback.Chunk chunk = new ActionCallback.Chunk();
    for (TreeNode node : nodesToInsert) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node;
      ActionCallback callback = updateNodeChildren(childNode, pass, null, false, canSmartExpand, forceUpdate, true, true);
      if (!callback.isDone()) {
        chunk.add(callback);
      }
    }
    return chunk.getWhenProcessed();
  }

  @NotNull
  private Promise<?> processExistingNodes(@NotNull DefaultMutableTreeNode node,
                                             @NotNull MutualMap<Object, Integer> elementToIndexMap,
                                             @NotNull TreeUpdatePass pass,
                                             boolean canSmartExpand,
                                             boolean forceUpdate,
                                             boolean wasExpanded,
                                             @Nullable LoadedChildren preloaded) {
    List<TreeNode> childNodes = TreeUtil.listChildren(node);
    return maybeYield(() -> {
      if (pass.isExpired()) return Promises.<Void>rejectedPromise();
      if (childNodes.isEmpty()) return Promises.resolvedPromise();


      List<Promise<?>> promises = new SmartList<>();
      for (TreeNode each : childNodes) {
        DefaultMutableTreeNode eachChild = (DefaultMutableTreeNode)each;
        if (isLoadingNode(eachChild)) {
          continue;
        }

        boolean childForceUpdate = isChildNodeForceUpdate(eachChild, forceUpdate, wasExpanded);

        promises.add(maybeYield(() -> {
          NodeDescriptor<?> descriptor = preloaded != null ? preloaded.getDescriptor(getElementFor(eachChild)) : null;
          NodeDescriptor<?> descriptorFromNode = getDescriptorFrom(eachChild);
          if (isValid(descriptor)) {
            eachChild.setUserObject(descriptor);
            if (descriptorFromNode != null) {
              descriptor.setChildrenSortingStamp(descriptorFromNode.getChildrenSortingStamp());
            }
          }
          else {
            descriptor = descriptorFromNode;
          }

          return processExistingNode(eachChild, descriptor, node, elementToIndexMap, pass, canSmartExpand,
                                     childForceUpdate, preloaded);
        }, pass, node));

        for (Promise<?> promise : promises) {
          if (promise.getState() == Promise.State.REJECTED) {
            return Promises.<Void>rejectedPromise();
          }
        }
      }
      return Promises.all(promises);
    }, pass, node);
  }

  private boolean isRerunNeeded(@NotNull TreeUpdatePass pass) {
    if (pass.isExpired() || !canInitiateNewActivity()) return false;

    boolean rerunBecauseTreeIsHidden = !pass.isExpired() && !isTreeShowing() && getUpdater().isInPostponeMode();

    return rerunBecauseTreeIsHidden || getUpdater().isRerunNeededFor(pass);
  }

  public static <T> T calculateYieldingToWriteAction(@NotNull Supplier<? extends T> producer) throws ProcessCanceledException {
    if (!Registry.is("ide.abstractTreeUi.BuildChildrenInBackgroundYieldingToWriteAction") ||
        ApplicationManager.getApplication().isDispatchThread()) {
      return producer.get();
    }
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null && indicator.isRunning()) {
      return producer.get();
    }

    Ref<T> result = new Ref<>();
    boolean succeeded = ProgressManager.getInstance().runInReadActionWithWriteActionPriority(
      () -> result.set(producer.get()),
      indicator
    );

    if (!succeeded || indicator != null && indicator.isCanceled()) {
      throw new ProcessCanceledException();
    }
    return result.get();
  }

  @FunctionalInterface
  private interface AsyncRunnable {
    @NotNull
    Promise<?> run();
  }

  @NotNull
  private Promise<?> maybeYield(@NotNull AsyncRunnable processRunnable, @NotNull TreeUpdatePass pass, DefaultMutableTreeNode node) {
    if (isRerunNeeded(pass)) {
      getUpdater().requeue(pass);
      return Promises.<Void>rejectedPromise();
    }

    if (canYield()) {
      AsyncPromise<?> result = new AsyncPromise<Void>();
      pass.setCurrentNode(node);
      boolean wasRun = yieldAndRun(new TreeRunnable("AbstractTreeUi.maybeYield") {
        @Override
        public void perform() {
          if (pass.isExpired()) {
            result.setError("expired");
            return;
          }

          if (isRerunNeeded(pass)) {
            runDone(new TreeRunnable("AbstractTreeUi.maybeYield: rerun") {
              @Override
              public void perform() {
                if (!pass.isExpired()) {
                  queueUpdate(getElementFor(node));
                }
              }
            });
            result.setError("requeue");
          }
          else {
            try {
              //noinspection unchecked
              execute(processRunnable).processed((Promise)result);
            }
            catch (ProcessCanceledException e) {
              pass.expire();
              cancelUpdate();
              result.setError("rejected");
            }
          }
        }
      }, pass);
      if (!wasRun) {
        result.setError("rejected");
      }
      return result;
    }
    else {
      try {
        return execute(processRunnable);
      }
      catch (ProcessCanceledException e) {
        pass.expire();
        cancelUpdate();
        return Promises.<Void>rejectedPromise();
      }
    }
  }

  @NotNull
  private Promise<?> execute(@NotNull AsyncRunnable runnable) throws ProcessCanceledException {
    try {
      if (!canInitiateNewActivity()) {
        throw new ProcessCanceledException();
      }

      Promise<?> promise = runnable.run();
      if (!canInitiateNewActivity()) {
        throw new ProcessCanceledException();
      }
      return promise;
    }
    catch (ProcessCanceledException e) {
      if (!isReleased()) {
        setCancelRequested(true);
        resetToReady();
      }
      throw e;
    }
  }

  private void execute(@NotNull Runnable runnable) throws ProcessCanceledException {
    try {
      if (!canInitiateNewActivity()) {
        throw new ProcessCanceledException();
      }

      runnable.run();

      if (!canInitiateNewActivity()) {
        throw new ProcessCanceledException();
      }
    }
    catch (ProcessCanceledException e) {
      if (!isReleased()) {
        setCancelRequested(true);
        resetToReady();
      }
      throw e;
    }
  }

  private boolean canInitiateNewActivity() {
    return !isCancelProcessed() && !myReleaseRequested && !isReleased();
  }

  private void resetToReady() {
    if (isReady()) {
      return;
    }

    if (myResettingToReadyNow.get()) {
      _getReady();
      return;
    }

    myResettingToReadyNow.set(true);

    invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.resetToReady: later") {
      @Override
      public void perform() {
        if (!myResettingToReadyNow.get()) {
          return;
        }

        Progressive[] progressives = myBatchIndicators.keySet().toArray(new Progressive[0]);
        for (Progressive each : progressives) {
          myBatchIndicators.remove(each).cancel();
          myBatchCallbacks.remove(each).setRejected();
        }

        resetToReadyNow();
      }
    });
  }

  @NotNull
  private ActionCallback resetToReadyNow() {
    if (isReleased()) return ActionCallback.REJECTED;

    assertIsDispatchThread();

    DefaultMutableTreeNode[] uc;
    synchronized (myUpdatingChildren) {
      uc = myUpdatingChildren.toArray(new DefaultMutableTreeNode[0]);
    }
    for (DefaultMutableTreeNode each : uc) {
      resetIncompleteNode(each);
    }


    Object[] bg = ArrayUtil.toObjectArray(myLoadedInBackground.keySet());
    for (Object each : bg) {
      DefaultMutableTreeNode node = getNodeForElement(each, false);
      if (node != null) {
        resetIncompleteNode(node);
      }
    }

    myUpdaterState = null;
    getUpdater().reset();

    myYieldingNow = false;
    myYieldingPasses.clear();
    myYieldingDoneRunnables.clear();

    myNodeActions.clear();
    myNodeChildrenActions.clear();

    synchronized (myUpdatingChildren) {
      myUpdatingChildren.clear();
    }
    myLoadedInBackground.clear();

    myDeferredExpansions.clear();
    myDeferredSelections.clear();

    ActionCallback result = _getReady();
    result.doWhenDone(new TreeRunnable("AbstractTreeUi.resetToReadyNow: on done") {
      @Override
      public void perform() {
        myResettingToReadyNow.set(false);
        setCancelRequested(false);
      }
    });

    maybeReady();

    return result;
  }

  void addToCancelled(@NotNull DefaultMutableTreeNode node) {
    myCancelledBuild.put(node, node);
  }

  private void removeFromCancelled(@NotNull DefaultMutableTreeNode node) {
    myCancelledBuild.remove(node);
  }

  public boolean isCancelled(@NotNull Object node) {
    return node instanceof DefaultMutableTreeNode && myCancelledBuild.containsKey(node);
  }

  private void resetIncompleteNode(@NotNull DefaultMutableTreeNode node) {
    if (myReleaseRequested) return;

    addToCancelled(node);

    if (!isExpanded(node, false)) {
      node.removeAllChildren();
      Object element = getElementFor(node);
      if (element != null && !getTreeStructure().isAlwaysLeaf(element)) {
        insertLoadingNode(node, true);
      }
    }
    else {
      removeFromUnbuilt(node);
      removeLoading(node, true);
    }
  }

  private boolean yieldAndRun(@NotNull Runnable runnable, @NotNull TreeUpdatePass pass) {
    myYieldingPasses.add(pass);
    myYieldingNow = true;
    yieldToEDT(new TreeRunnable("AbstractTreeUi.yieldAndRun") {
      @Override
      public void perform() {
        if (isReleased()) return;

        runOnYieldingDone(new TreeRunnable("AbstractTreeUi.yieldAndRun: inner") {
          @Override
          public void perform() {
            if (isReleased()) return;

            executeYieldingRequest(runnable, pass);
          }
        });
      }
    });

    return true;
  }

  private boolean isYieldingNow() {
    return myYieldingNow;
  }

  private boolean hasScheduledUpdates() {
    return getUpdater().hasNodesToUpdate();
  }

  public boolean isReady() {
    return isReady(false);
  }

  boolean isCancelledReady() {
    return isReady(false) && !myCancelledBuild.isEmpty();
  }

  public boolean isReady(boolean attempt) {
    if (attempt && myStateLock.isLocked()) return false;

    try (LockToken ignored = attempt ? attemptLock() : acquireLock()) {
      return isIdle() && !hasPendingWork() && !isNodeActionsPending();
    }
    catch (InterruptedException e) {
      LOG.info(e);
      return false;
    }
  }

  @NotNull
  @NonNls
  public String getStatus() {
    return "isReady=" + isReady() + "\n" +
           " isIdle=" + isIdle() + "\n" +
           "  isYeildingNow=" + isYieldingNow() + "\n" +
           "  isWorkerBusy=" + isWorkerBusy() + "\n" +
           "  hasUpdatingChildrenNow=" + hasUpdatingChildrenNow() + "\n" +
           "  isLoadingInBackgroundNow=" + isLoadingInBackgroundNow() + "\n" +
           " hasPendingWork=" + hasPendingWork() + "\n" +
           "  hasNodesToUpdate=" + hasNodesToUpdate() + "\n" +
           "  updaterState=" + myUpdaterState + "\n" +
           "  hasScheduledUpdates=" + hasScheduledUpdates() + "\n" +
           "  isPostponedMode=" + getUpdater().isInPostponeMode() + "\n" +
           " nodeActions=" + myNodeActions.keySet() + "\n" +
           " nodeChildrenActions=" + myNodeChildrenActions.keySet() + "\n" +
           "isReleased=" + isReleased() + "\n" +
           " isReleaseRequested=" + isReleaseRequested() + "\n" +
           "isCancelProcessed=" + isCancelProcessed() + "\n" +
           " isCancelRequested=" + myCancelRequest + "\n" +
           " isResettingToReadyNow=" + myResettingToReadyNow + "\n" +
           "canInitiateNewActivity=" + canInitiateNewActivity() + "\n" +
           "batchIndicators=" + myBatchIndicators;
  }

  public boolean hasPendingWork() {
    return hasNodesToUpdate() ||
           myUpdaterState != null && myUpdaterState.isProcessingNow() ||
           hasScheduledUpdates() && !getUpdater().isInPostponeMode();
  }

  public boolean isIdle() {
    return !isYieldingNow() && !isWorkerBusy() && !hasUpdatingChildrenNow() && !isLoadingInBackgroundNow();
  }

  private void executeYieldingRequest(@NotNull Runnable runnable, @NotNull TreeUpdatePass pass) {
    try {
      try {
        myYieldingPasses.remove(pass);

        if (!canInitiateNewActivity()) {
          throw new ProcessCanceledException();
        }

        runnable.run();
      }
      finally {
        if (!isReleased()) {
          maybeYieldingFinished();
        }
      }
    }
    catch (ProcessCanceledException e) {
      resetToReady();
    }
  }

  private void maybeYieldingFinished() {
    if (myYieldingPasses.isEmpty()) {
      myYieldingNow = false;
      flushPendingNodeActions();
    }
  }

  void maybeReady() {
    assertIsDispatchThread();

    if (isReleased()) return;

    boolean ready = isReady(true);
    if (!ready) return;
    myRevalidatedObjects.clear();

    setCancelRequested(false);
    myResettingToReadyNow.set(false);

    myInitialized.setDone();

    if (canInitiateNewActivity()) {
      if (myUpdaterState != null && !myUpdaterState.isProcessingNow()) {
        UpdaterTreeState oldState = myUpdaterState;
        if (!myUpdaterState.restore(null)) {
          setUpdaterState(oldState);
        }

        if (!isReady(true)) return;
      }
    }

    setHoldSize(false);

    if (myTree.isShowing()) {
      if (getBuilder().isToEnsureSelectionOnFocusGained() && Registry.is("ide.tree.ensureSelectionOnFocusGained")) {
        TreeUtil.ensureSelection(myTree);
      }
    }

    if (myInitialized.isDone()) {
      if (isReleaseRequested() || isCancelProcessed()) {
        myBusyObject.onReady(this);
      } else {
        myBusyObject.onReady();
      }
    }

    if (canInitiateNewActivity()) {
      TreePath[] selection = getTree().getSelectionPaths();
      Rectangle visible = getTree().getVisibleRect();
      if (selection != null) {
        for (TreePath each : selection) {
          Rectangle bounds = getTree().getPathBounds(each);
          if (bounds != null && (visible.contains(bounds) || visible.intersects(bounds))) {
            getTree().repaint(bounds);
          }
        }
      }
    }
  }

  private void flushPendingNodeActions() {
    DefaultMutableTreeNode[] nodes = myPendingNodeActions.toArray(new DefaultMutableTreeNode[0]);
    myPendingNodeActions.clear();

    for (DefaultMutableTreeNode each : nodes) {
      processNodeActionsIfReady(each);
    }

    Runnable[] actions = myYieldingDoneRunnables.toArray(ArrayUtil.EMPTY_RUNNABLE_ARRAY);
    for (Runnable each : actions) {
      if (!isYieldingNow()) {
        myYieldingDoneRunnables.remove(each);
        each.run();
      }
    }

    maybeReady();
  }

  protected void runOnYieldingDone(@NotNull Runnable onDone) {
    getBuilder().runOnYieldingDone(onDone);
  }

  protected void yieldToEDT(@NotNull Runnable runnable) {
    getBuilder().yieldToEDT(runnable);
  }

  @NotNull
  private MutualMap<Object, Integer> loadElementsFromStructure(NodeDescriptor<?> descriptor,
                                                               @Nullable LoadedChildren preloadedChildren) {
    MutualMap<Object, Integer> elementToIndexMap = new MutualMap<>(true);
    Object element = getElementFromDescriptor(descriptor);
    if (!isValid(element)) return elementToIndexMap;


    List<Object> children = preloadedChildren != null
                            ? preloadedChildren.getElements()
                            : Arrays.asList(getChildrenFor(element));
    int index = 0;
    for (Object child : children) {
      if (!isValid(child)) continue;
      elementToIndexMap.put(child, index);
      index++;
    }
    return elementToIndexMap;
  }

  public static boolean isLoadingNode(Object node) {
    return node instanceof LoadingNode;
  }

  @NotNull
  private AsyncResult<List<TreeNode>> collectNodesToInsert(NodeDescriptor<?> descriptor,
                                                           @NotNull MutualMap<Object, Integer> elementToIndexMap,
                                                           DefaultMutableTreeNode parent,
                                                           boolean addLoadingNode,
                                                           @NotNull LoadedChildren loadedChildren) {
    AsyncResult<List<TreeNode>> result = new AsyncResult<>();

    List<TreeNode> nodesToInsert = new ArrayList<>();
    Collection<Object> allElements = elementToIndexMap.getKeys();

    ActionCallback processingDone = allElements.isEmpty() ? ActionCallback.DONE : new ActionCallback(allElements.size());

    for (Object child : allElements) {
      Integer index = elementToIndexMap.getValue(child);
      boolean needToUpdate = false;
      NodeDescriptor<?> loadedDesc = loadedChildren.getDescriptor(child);
      NodeDescriptor<?> childDescr;
      if (!isValid(loadedDesc, descriptor)) {
        childDescr = getTreeStructure().createDescriptor(child, descriptor);
        needToUpdate = true;
      }
      else {
        childDescr = loadedDesc;
      }

      if (index == null) {
        index = Integer.MAX_VALUE;
        needToUpdate = true;
      }

      childDescr.setIndex(index.intValue());

      ActionCallback update = new ActionCallback();
      if (needToUpdate) {
        update(childDescr, false)
          .onSuccess(changes -> {
            loadedChildren.putDescriptor(child, childDescr, changes);
            update.setDone();
          });
      }
      else {
        update.setDone();
      }

      update.doWhenDone(new TreeRunnable("AbstractTreeUi.collectNodesToInsert: on done update") {
        @Override
        public void perform() {
          Object element = getElementFromDescriptor(childDescr);
          if (!isNodeNull(element)) {
            DefaultMutableTreeNode node = getNodeForElement(element, false);
            if (node == null || node.getParent() != parent) {
              DefaultMutableTreeNode childNode = createChildNode(childDescr);
              if (addLoadingNode || getBuilder().isAlwaysShowPlus(childDescr)) {
                insertLoadingNode(childNode, true);
              }
              else {
                addToUnbuilt(childNode);
              }
              nodesToInsert.add(childNode);
              createMapping(element, childNode);
            }
          }
          processingDone.setDone();
        }
      });
    }

    processingDone.doWhenDone(new TreeRunnable("AbstractTreeUi.collectNodesToInsert: on done processing") {
      @Override
      public void perform() {
        result.setDone(nodesToInsert);
      }
    });

    return result;
  }

  @NotNull
  protected DefaultMutableTreeNode createChildNode(NodeDescriptor<?> descriptor) {
    return new ElementNode(this, descriptor);
  }

  protected boolean canYield() {
    return myCanYield && myYieldingUpdate.asBoolean();
  }

  private long getClearOnHideDelay() {
    return myClearOnHideDelay;
  }

  @NotNull
  public ActionCallback getInitialized() {
    return myInitialized;
  }

  public ActionCallback getReady(@NotNull Object requestor) {
    return myBusyObject.getReady(requestor);
  }

  private ActionCallback _getReady() {
    return getReady(this);
  }

  private void addToUpdatingChildren(@NotNull DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      myUpdatingChildren.add(node);
    }
  }

  private void removeFromUpdatingChildren(@NotNull DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      myUpdatingChildren.remove(node);
    }
  }

  boolean isUpdatingChildrenNow(DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      return myUpdatingChildren.contains(node);
    }
  }

  boolean isParentUpdatingChildrenNow(@NotNull DefaultMutableTreeNode node) {
    synchronized (myUpdatingChildren) {
      DefaultMutableTreeNode eachParent = (DefaultMutableTreeNode)node.getParent();
      while (eachParent != null) {
        if (myUpdatingChildren.contains(eachParent)) return true;

        eachParent = (DefaultMutableTreeNode)eachParent.getParent();
      }

      return false;
    }
  }

  private boolean hasUpdatingChildrenNow() {
    synchronized (myUpdatingChildren) {
      return !myUpdatingChildren.isEmpty();
    }
  }

  @NotNull
  Map<Object, List<NodeAction>> getNodeActions() {
    return myNodeActions;
  }

  @NotNull
  List<Object> getLoadedChildrenFor(@NotNull Object element) {
    List<Object> result = new ArrayList<>();

    DefaultMutableTreeNode node = getNodeForElement(element, false);
    if (node != null) {
      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode each = node.getChildAt(i);
        if (isLoadingNode(each)) continue;

        result.add(getElementFor(each));
      }
    }

    return result;
  }

  boolean hasNodesToUpdate() {
    return getUpdater().hasNodesToUpdate();
  }

  @NotNull
  public List<Object> getExpandedElements() {
    List<Object> result = new ArrayList<>();
    if (isReleased()) return result;

    Enumeration<TreePath> enumeration = myTree.getExpandedDescendants(getPathFor(getRootNode()));
    if (enumeration != null) {
      while (enumeration.hasMoreElements()) {
        TreePath each = enumeration.nextElement();
        Object eachElement = getElementFor(each.getLastPathComponent());
        if (eachElement != null) {
          result.add(eachElement);
        }
      }
    }

    return result;
  }

  @NotNull
  public ActionCallback cancelUpdate() {
    if (isReleased()) return ActionCallback.REJECTED;

    setCancelRequested(true);

    ActionCallback done = new ActionCallback();

    invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.cancelUpdate") {
      @Override
      public void perform() {
        if (isReleased()) {
          done.setRejected();
          return;
        }

        if (myResettingToReadyNow.get()) {
          _getReady().notify(done);
        } else if (isReady()) {
          resetToReadyNow();
          done.setDone();
        } else {
          if (isIdle() && hasPendingWork()) {
            resetToReadyNow();
            done.setDone();
          } else {
            _getReady().notify(done);
          }
        }

        maybeReady();
      }
    });

    if (isEdt() || isPassthroughMode()) {
      maybeReady();
    }

    return done;
  }

  private void setCancelRequested(boolean requested) {
    try (LockToken ignored = isUnitTestingMode() ? acquireLock() : attemptLock()) {
      myCancelRequest.set(requested);
    }
    catch (InterruptedException ignored) {
    }
  }

  @Nullable
  private LockToken attemptLock() throws InterruptedException {
    return LockToken.attemptLock(myStateLock, Registry.intValue("ide.tree.uiLockAttempt"));
  }

  @NotNull
  private LockToken acquireLock() {
    return LockToken.acquireLock(myStateLock);
  }

  @NotNull
  public ActionCallback batch(@NotNull Progressive progressive) {
    assertIsDispatchThread();

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    ActionCallback callback = new ActionCallback();

    myBatchIndicators.put(progressive, indicator);
    myBatchCallbacks.put(progressive, callback);

    try {
      progressive.run(indicator);
    }
    catch (ProcessCanceledException e) {
      resetToReadyNow().doWhenProcessed(new TreeRunnable("AbstractTreeUi.batch: catch") {
        @Override
        public void perform() {
          callback.setRejected();
        }
      });
      return callback;
    }
    finally {
      if (isReleased()) return ActionCallback.REJECTED;

      _getReady().doWhenDone(new TreeRunnable("AbstractTreeUi.batch: finally") {
        @Override
        public void perform() {
          if (myBatchIndicators.containsKey(progressive)) {
            ProgressIndicator indicator = myBatchIndicators.remove(progressive);
            myBatchCallbacks.remove(progressive);

            if (indicator.isCanceled()) {
              callback.setRejected();
            } else {
              callback.setDone();
            }
          } else {
            callback.setRejected();
          }
        }
      });

      maybeReady();
    }


    return callback;
  }

  boolean isCancelProcessed() {
    return myCancelRequest.get() || myResettingToReadyNow.get();
  }

  boolean isToPaintSelection() {
    return isReady(true) || !mySelectionIsAdjusted;
  }

  boolean isReleaseRequested() {
    return myReleaseRequested;
  }

  public void executeUserRunnable(@NotNull Runnable runnable) {
    try {
      myUserRunnables.add(runnable);
      runnable.run();
    }
    finally {
      myUserRunnables.remove(runnable);
    }
  }

  static class ElementNode extends DefaultMutableTreeNode {

    Set<Object> myElements = new HashSet<>();
    AbstractTreeUi myUi;

    ElementNode(AbstractTreeUi ui, NodeDescriptor descriptor) {
      super(descriptor);
      myUi = ui;
    }

    @Override
    public void insert(MutableTreeNode newChild, int childIndex) {
      super.insert(newChild, childIndex);
      Object element = myUi.getElementFor(newChild);
      if (element != null) {
        myElements.add(element);
      }
    }

    @Override
    public void remove(int childIndex) {
      TreeNode node = getChildAt(childIndex);
      super.remove(childIndex);
      Object element = myUi.getElementFor(node);
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
    return getUpdatingParent(kid) != null;
  }

  @Nullable
  private DefaultMutableTreeNode getUpdatingParent(DefaultMutableTreeNode kid) {
    DefaultMutableTreeNode eachParent = kid;
    while (eachParent != null) {
      if (isUpdatingChildrenNow(eachParent)) return eachParent;
      eachParent = (DefaultMutableTreeNode)eachParent.getParent();
    }

    return null;
  }

  private boolean isLoadedInBackground(Object element) {
    return getLoadedInBackground(element) != null;
  }

  private UpdateInfo getLoadedInBackground(Object element) {
    synchronized (myLoadedInBackground) {
      return isNodeNull(element) ? null : myLoadedInBackground.get(element);
    }
  }

  private void addToLoadedInBackground(Object element, UpdateInfo info) {
    if (isNodeNull(element)) return;
    synchronized (myLoadedInBackground) {
      warnMap("put into myLoadedInBackground: ", myLoadedInBackground);
      myLoadedInBackground.put(element, info);
    }
  }

  private void removeFromLoadedInBackground(Object element) {
    if (isNodeNull(element)) return;
    synchronized (myLoadedInBackground) {
      warnMap("remove from myLoadedInBackground: ", myLoadedInBackground);
      myLoadedInBackground.remove(element);
    }
  }

  private boolean isLoadingInBackgroundNow() {
    synchronized (myLoadedInBackground) {
      return !myLoadedInBackground.isEmpty();
    }
  }

  private void queueBackgroundUpdate(@NotNull UpdateInfo updateInfo, @NotNull DefaultMutableTreeNode node) {
    assertIsDispatchThread();

    Object oldElementFromDescriptor = getElementFromDescriptor(updateInfo.getDescriptor());
    if (isNodeNull(oldElementFromDescriptor)) return;

    UpdateInfo loaded = getLoadedInBackground(oldElementFromDescriptor);
    if (loaded != null) {
      loaded.apply(updateInfo);
      return;
    }

    addToLoadedInBackground(oldElementFromDescriptor, updateInfo);

    maybeSetBusyAndScheduleWaiterForReady(true, oldElementFromDescriptor);

    if (!isNodeBeingBuilt(node)) {
      LoadingNode loadingNode = new LoadingNode(getLoadingNodeText());
      myTreeModel.insertNodeInto(loadingNode, node, node.getChildCount());
    }

    removeFromUnbuilt(node);

    Ref<LoadedChildren> children = new Ref<>();
    Ref<Object> elementFromDescriptor = new Ref<>();

    DefaultMutableTreeNode[] nodeToProcessActions = new DefaultMutableTreeNode[1];

    TreeConsumer<Void> finalizeRunnable = new TreeConsumer<>("AbstractTreeUi.queueBackgroundUpdate: finalize") {
      @Override
      public void perform() {
        invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.queueBackgroundUpdate: finalize later") {
          @Override
          public void perform() {
            if (isReleased()) return;

            removeLoading(node, false);
            removeFromLoadedInBackground(elementFromDescriptor.get());
            removeFromLoadedInBackground(oldElementFromDescriptor);

            if (nodeToProcessActions[0] != null) {
              processNodeActionsIfReady(nodeToProcessActions[0]);
            }
          }
        });
      }
    };


    Runnable buildRunnable = new TreeRunnable("AbstractTreeUi.queueBackgroundUpdate: build") {
      @Override
      public void perform() {
        if (updateInfo.getPass().isExpired()) {
          finalizeRunnable.run();
          return;
        }

        if (!updateInfo.isDescriptorIsUpToDate()) {
          update(updateInfo.getDescriptor(), true);
        }

        if (!updateInfo.isUpdateChildren()) {
          nodeToProcessActions[0] = node;
          return;
        }

        Object element = getElementFromDescriptor(updateInfo.getDescriptor());
        if (element == null) {
          removeFromLoadedInBackground(oldElementFromDescriptor);
          finalizeRunnable.run();
          return;
        }

        elementFromDescriptor.set(element);

        Object[] loadedElements = getChildrenFor(element);

        LoadedChildren loaded = new LoadedChildren(loadedElements);
        for (Object each : loadedElements) {
          NodeDescriptor<?> existingDesc = getDescriptorFrom(getNodeForElement(each, true));
          NodeDescriptor<?> eachChildDescriptor = isValid(existingDesc, updateInfo.getDescriptor()) ? existingDesc : getTreeStructure().createDescriptor(each, updateInfo.getDescriptor());
          execute(new TreeRunnable("AbstractTreeUi.queueBackgroundUpdate") {
            @Override
            public void perform() {
              try {
                loaded.putDescriptor(each, eachChildDescriptor, update(eachChildDescriptor, true).blockingGet(0));
              }
              catch (TimeoutException | ExecutionException e) {
                LOG.error(e);
              }
            }
          });
        }

        children.set(loaded);
      }

      @NotNull
      @NonNls
      @Override
      public String toString() {
        return "runnable=" + oldElementFromDescriptor;
      }
    };

    Runnable updateRunnable = new TreeRunnable("AbstractTreeUi.queueBackgroundUpdate: update") {
      @Override
      public void perform() {
        if (updateInfo.getPass().isExpired()) {
          finalizeRunnable.run();
          return;
        }

        if (children.get() == null) {
          finalizeRunnable.run();
          return;
        }

        if (isRerunNeeded(updateInfo.getPass())) {
          removeFromLoadedInBackground(elementFromDescriptor.get());
          getUpdater().requeue(updateInfo.getPass());
          return;
        }

        removeFromLoadedInBackground(elementFromDescriptor.get());

        if (myUnbuiltNodes.contains(node)) {
          Pair<Boolean, LoadedChildren> unbuilt =
            processUnbuilt(node, updateInfo.getDescriptor(), updateInfo.getPass(), isExpanded(node, updateInfo.isWasExpanded()),
                           children.get());
          if (unbuilt.getFirst()) {
            nodeToProcessActions[0] = node;
            return;
          }
        }

        ActionCallback callback = updateNodeChildren(node, updateInfo.getPass(), children.get(),
                                                     true, updateInfo.isCanSmartExpand(), updateInfo.isForceUpdate(), true, true);
        callback.doWhenDone(new TreeRunnable("AbstractTreeUi.queueBackgroundUpdate: on done updateNodeChildren") {
          @Override
          public void perform() {
            if (isRerunNeeded(updateInfo.getPass())) {
              getUpdater().requeue(updateInfo.getPass());
              return;
            }

            Object element = elementFromDescriptor.get();

            if (element != null) {
              removeLoading(node, false);
              nodeToProcessActions[0] = node;
            }
          }
        });
      }
    };
    queueToBackground(buildRunnable, updateRunnable)
      .onSuccess(finalizeRunnable)
      .onError(new TreeConsumer<>("AbstractTreeUi.queueBackgroundUpdate: on rejected") {
        @Override
        public void perform() {
          updateInfo.getPass().expire();
        }
      });
  }

  private boolean isExpanded(@NotNull DefaultMutableTreeNode node, boolean isExpanded) {
    return isExpanded || myTree.isExpanded(getPathFor(node));
  }

  private void removeLoading(@NotNull DefaultMutableTreeNode parent, boolean forced) {
    if (!forced && myUnbuiltNodes.contains(parent) && !myCancelledBuild.containsKey(parent)) {
      return;
    }

    boolean reallyRemoved = false;
    for (int i = 0; i < parent.getChildCount(); i++) {
      TreeNode child = parent.getChildAt(i);
      if (removeIfLoading(child)) {
        reallyRemoved = true;
        i--;
      }
    }

    maybeReady();
    if (reallyRemoved) {
      nodeStructureChanged(parent);
    }
  }

  private void processNodeActionsIfReady(@NotNull DefaultMutableTreeNode node) {
    assertIsDispatchThread();

    if (isNodeBeingBuilt(node)) return;

    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;


    if (isYieldingNow()) {
      myPendingNodeActions.add(node);
      return;
    }

    Object element = getElementFromDescriptor(descriptor);

    boolean childrenReady = !isLoadedInBackground(element) && !isUpdatingChildrenNow(node);

    processActions(node, element, myNodeActions, childrenReady ? myNodeChildrenActions : null);
    if (childrenReady) {
      processActions(node, element, myNodeChildrenActions, null);
    }
    warnMap("myNodeActions: processNodeActionsIfReady: ", myNodeActions);
    warnMap("myNodeChildrenActions: processNodeActionsIfReady: ", myNodeChildrenActions);

    if (!isUpdatingParent(node) && !isWorkerBusy()) {
      UpdaterTreeState state = myUpdaterState;
      if (myNodeActions.isEmpty() && state != null && !state.isProcessingNow()) {
        if (canInitiateNewActivity()) {
          if (!state.restore(childrenReady ? node : null)) {
            setUpdaterState(state);
          }
        }
      }
    }

    maybeReady();
  }


  private static void processActions(@NotNull DefaultMutableTreeNode node,
                                     Object element,
                                     @NotNull Map<Object, List<NodeAction>> nodeActions,
                                     @Nullable Map<Object, List<NodeAction>> secondaryNodeAction) {
    List<NodeAction> actions = nodeActions.get(element);
    if (actions != null) {
      nodeActions.remove(element);

      List<NodeAction> secondary = secondaryNodeAction != null ? secondaryNodeAction.get(element) : null;
      for (NodeAction each : actions) {
        if (secondary != null) {
          secondary.remove(each);
        }
        each.onReady(node);
      }
    }
  }


  private boolean canSmartExpand(DefaultMutableTreeNode node, boolean canSmartExpand) {
    if (!canInitiateNewActivity()) return false;
    if (!getBuilder().isSmartExpand()) return false;

    boolean smartExpand = canSmartExpand && !myNotForSmartExpand.contains(node);
    Object element = getElementFor(node);
    return smartExpand && element != null && validateAutoExpand(true, element);
  }

  private void processSmartExpand(@NotNull DefaultMutableTreeNode node, boolean canSmartExpand, boolean forced) {
    if (!canInitiateNewActivity()) return;
    if (!getBuilder().isSmartExpand()) return;

    boolean can = canSmartExpand(node, canSmartExpand);

    if (!can && !forced) return;

    if (isNodeBeingBuilt(node) && !forced) {
      addNodeAction(getElementFor(node), true, node1 -> processSmartExpand(node1, canSmartExpand, true));
    }
    else {
      TreeNode child = getChildForSmartExpand(node);
      if (child != null) {
        TreePath childPath = new TreePath(node.getPath()).pathByAddingChild(child);
        processInnerChange(new TreeRunnable("AbstractTreeUi.processSmartExpand") {
          @Override
          public void perform() {
            myTree.expandPath(childPath);
          }
        });
      }
    }
  }

  @Nullable
  private static TreeNode getChildForSmartExpand(@NotNull DefaultMutableTreeNode node) {
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

  public static boolean isLoadingChildrenFor(Object nodeObject) {
    if (!(nodeObject instanceof DefaultMutableTreeNode node)) return false;

    int loadingNodes = 0;
    for (int i = 0; i < Math.min(node.getChildCount(), 2); i++) {
      TreeNode child = node.getChildAt(i);
      if (isLoadingNode(child)) {
        loadingNodes++;
      }
    }
    return loadingNodes > 0 && loadingNodes == node.getChildCount();
  }

  boolean isParentLoadingInBackground(@NotNull Object nodeObject) {
    return getParentLoadingInBackground(nodeObject) != null;
  }

  @Nullable
  private DefaultMutableTreeNode getParentLoadingInBackground(@NotNull Object nodeObject) {
    if (!(nodeObject instanceof DefaultMutableTreeNode node)) return null;

    TreeNode eachParent = node.getParent();

    while (eachParent != null) {
      eachParent = eachParent.getParent();
      if (eachParent instanceof DefaultMutableTreeNode) {
        Object eachElement = getElementFor(eachParent);
        if (isLoadedInBackground(eachElement)) return (DefaultMutableTreeNode)eachParent;
      }
    }

    return null;
  }

  private static @Nls String getLoadingNodeText() {
    return IdeBundle.message("progress.searching");
  }

  @NotNull
  private Promise<?> processExistingNode(@NotNull DefaultMutableTreeNode childNode,
                                         NodeDescriptor<?> childDescriptor,
                                         @NotNull DefaultMutableTreeNode parentNode,
                                         @NotNull MutualMap<Object, Integer> elementToIndexMap,
                                         @NotNull TreeUpdatePass pass,
                                         boolean canSmartExpand,
                                         boolean forceUpdate,
                                         @Nullable LoadedChildren parentPreloadedChildren) {
    if (pass.isExpired()) {
      return Promises.<Void>rejectedPromise();
    }

    if (childDescriptor == null) {
      pass.expire();
      return Promises.<Void>rejectedPromise();
    }
    Object oldElement = getElementFromDescriptor(childDescriptor);
    if (isNodeNull(oldElement)) {
      // if a tree node with removed element was not properly removed from a tree model
      // we must not ignore this situation and should remove a wrong node
      removeNodeFromParent(childNode, true);
      doUpdateNode(parentNode);
      return Promises.<Void>resolvedPromise();
    }

    Promise<Boolean> update;
    if (parentPreloadedChildren != null && parentPreloadedChildren.getDescriptor(oldElement) == childDescriptor) {
      update = Promises.resolvedPromise(parentPreloadedChildren.isUpdated(oldElement));
    }
    else {
      update = update(childDescriptor, false);
    }

    AsyncPromise<Void> result = new AsyncPromise<>();
    Ref<NodeDescriptor<?>> childDesc = new Ref<>(childDescriptor);

    update
      .onSuccess(isChanged -> {
        AtomicBoolean changes = new AtomicBoolean(isChanged);
        AtomicBoolean forceRemapping = new AtomicBoolean();
        Ref<Object> newElement = new Ref<>(getElementFromDescriptor(childDesc.get()));

        Integer index = newElement.get() == null ? null : elementToIndexMap.getValue(getElementFromDescriptor(childDesc.get()));
        Promise<Boolean> promise;
        if (index == null) {
          promise = Promises.resolvedPromise(false);
        }
        else {
          Object elementFromMap = elementToIndexMap.getKey(index);
          if (elementFromMap != newElement.get() && elementFromMap.equals(newElement.get())) {
            if (isInStructure(elementFromMap) && isInStructure(newElement.get())) {
              AsyncPromise<Boolean> updateIndexDone = new AsyncPromise<>();
              promise = updateIndexDone;
              NodeDescriptor<?> parentDescriptor = getDescriptorFrom(parentNode);
              if (parentDescriptor != null) {
                childDesc.set(getTreeStructure().createDescriptor(elementFromMap, parentDescriptor));
                NodeDescriptor<?> oldDesc = getDescriptorFrom(childNode);
                if (isValid(oldDesc)) {
                  childDesc.get().applyFrom(oldDesc);
                }

                childNode.setUserObject(childDesc.get());
                newElement.set(elementFromMap);
                forceRemapping.set(true);
                update(childDesc.get(), false)
                  .onSuccess(isChanged1 -> {
                    changes.set(isChanged1);
                    updateIndexDone.setResult(isChanged1);
                  });
              }
              // todo why we don't process promise here?
            }
            else {
              promise = Promises.resolvedPromise(changes.get());
            }
          }
          else {
            promise = Promises.resolvedPromise(changes.get());
          }

          promise
            .onSuccess(new TreeConsumer<>("AbstractTreeUi.processExistingNode: on done index updating after update") {
              @Override
              public void perform() {
                if (childDesc.get().getIndex() != index.intValue()) {
                  changes.set(true);
                }
                childDesc.get().setIndex(index.intValue());
              }
            });
        }

        promise
          .onSuccess(new TreeConsumer<>("AbstractTreeUi.processExistingNode: on done index updating") {
            @Override
            public void perform() {
              if (!oldElement.equals(newElement.get()) || forceRemapping.get()) {
                removeMapping(oldElement, childNode, newElement.get());
                Object newE = newElement.get();
                if (!isNodeNull(newE)) {
                  createMapping(newE, childNode);
                }
                NodeDescriptor<?> parentDescriptor = getDescriptorFrom(parentNode);
                if (parentDescriptor != null) {
                  parentDescriptor.setChildrenSortingStamp(-1);
                }
              }

              if (index == null) {
                int selectedIndex = -1;
                if (TreeBuilderUtil.isNodeOrChildSelected(myTree, childNode)) {
                  selectedIndex = parentNode.getIndex(childNode);
                }

                if (childNode.getParent() instanceof DefaultMutableTreeNode parent) {
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
                result.setResult(null);
              }
              else {
                elementToIndexMap.remove(getElementFromDescriptor(childDesc.get()));
                updateNodeChildren(childNode, pass, null, false, canSmartExpand, forceUpdate, true, true)
                  .doWhenDone(() -> result.setResult(null));
              }
            }
          });
      });
    return result;
  }

  private void adjustSelectionOnChildRemove(@NotNull DefaultMutableTreeNode parentNode, int selectedIndex, Object disposedElement) {
    if (selectedIndex >= 0 && !getSelectedElements().isEmpty()) return;

    DefaultMutableTreeNode node = disposedElement == null ? null : getNodeForElement(disposedElement, false);
    if (node != null && isValidForSelectionAdjusting(node)) {
      Object newElement = getElementFor(node);
      addSelectionPath(getPathFor(node), true, getExpiredElementCondition(newElement), disposedElement);
      return;
    }


    if (selectedIndex >= 0) {
      if (parentNode.getChildCount() > 0) {
        if (parentNode.getChildCount() > selectedIndex) {
          TreeNode newChildNode = parentNode.getChildAt(selectedIndex);
          if (isValidForSelectionAdjusting(newChildNode)) {
            addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChildNode)), true, getExpiredElementCondition(disposedElement),
                             disposedElement);
          }
        }
        else {
          TreeNode newChild = parentNode.getChildAt(parentNode.getChildCount() - 1);
          if (isValidForSelectionAdjusting(newChild)) {
            addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChild)), true, getExpiredElementCondition(disposedElement),
                             disposedElement);
          }
        }
      }
      else {
        addSelectionPath(new TreePath(myTreeModel.getPathToRoot(parentNode)), true, getExpiredElementCondition(disposedElement),
                         disposedElement);
      }
    }
  }

  private boolean isValidForSelectionAdjusting(@NotNull TreeNode node) {
    if (!myTree.isRootVisible() && getRootNode() == node) return false;

    if (isLoadingNode(node)) return true;

    Object elementInTree = getElementFor(node);
    if (elementInTree == null) return false;

    TreeNode parentNode = node.getParent();
    Object parentElementInTree = getElementFor(parentNode);
    if (parentElementInTree == null) return false;

    Object parentElement = getTreeStructure().getParentElement(elementInTree);

    return parentElementInTree.equals(parentElement);
  }

  @NotNull
  private Condition<?> getExpiredElementCondition(Object element) {
    return __ -> isInStructure(element);
  }

  private void addSelectionPath(@NotNull TreePath path,
                                boolean isAdjustedSelection,
                                Condition<?> isExpiredAdjustment,
                                @Nullable Object adjustmentCause) {
    processInnerChange(new TreeRunnable("AbstractTreeUi.addSelectionPath") {
      @Override
      public void perform() {
        TreePath toSelect = null;

        if (isLoadingNode(path.getLastPathComponent())) {
          TreePath parentPath = path.getParentPath();
          if (parentPath != null && isValidForSelectionAdjusting((TreeNode)parentPath.getLastPathComponent())) {
            toSelect = parentPath;
          }
        }
        else {
          toSelect = path;
        }

        if (toSelect != null) {
          mySelectionIsAdjusted = isAdjustedSelection;

          myTree.addSelectionPath(toSelect);

          if (isAdjustedSelection && myUpdaterState != null) {
            Object toSelectElement = getElementFor(toSelect.getLastPathComponent());
            myUpdaterState.addAdjustedSelection(toSelectElement, isExpiredAdjustment, adjustmentCause);
          }
        }
      }
    });
  }

  @NotNull
  private static TreePath getPathFor(@NotNull TreeNode node) {
    if (node instanceof DefaultMutableTreeNode) {
      return new TreePath(((DefaultMutableTreeNode)node).getPath());
    }
    else {
      List<TreeNode> nodes = new ArrayList<>();
      TreeNode eachParent = node;
      while (eachParent != null) {
        nodes.add(eachParent);
        eachParent = eachParent.getParent();
      }

      return new TreePath(ArrayUtil.toObjectArray(nodes));
    }
  }


  private void removeNodeFromParent(@NotNull MutableTreeNode node, boolean willAdjustSelection) {
    processInnerChange(new TreeRunnable("AbstractTreeUi.removeNodeFromParent") {
      @Override
      public void perform() {
        if (willAdjustSelection) {
          TreePath path = getPathFor(node);
          if (myTree.isPathSelected(path)) {
            myTree.removeSelectionPath(path);
          }
        }

        if (node.getParent() != null) {
          myTreeModel.removeNodeFromParent(node);
        }
      }
    });
  }

  private void expandPath(@NotNull TreePath path, boolean canSmartExpand) {
    processInnerChange(new TreeRunnable("AbstractTreeUi.expandPath") {
      @Override
      public void perform() {
        if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node) {
          if (node.getChildCount() > 0 && !myTree.isExpanded(path)) {
            if (!canSmartExpand) {
              myNotForSmartExpand.add(node);
            }
            try {
              myRequestedExpand = path;
              myTree.expandPath(path);
              processSmartExpand(node, canSmartExpand, false);
            }
            finally {
              myNotForSmartExpand.remove(node);
              myRequestedExpand = null;
            }
          }
          else {
            processNodeActionsIfReady(node);
          }
        }
      }
    });
  }

  private void processInnerChange(Runnable runnable) {
    if (myUpdaterState == null) {
      setUpdaterState(new UpdaterTreeState(this));
    }

    myUpdaterState.process(runnable);
  }

  private boolean isInnerChange() {
    return myUpdaterState != null && myUpdaterState.isProcessingNow() && myUserRunnables.isEmpty();
  }

  private void makeLoadingOrLeafIfNoChildren(@NotNull DefaultMutableTreeNode node) {
    TreePath path = getPathFor(node);

    insertLoadingNode(node, true);

    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;

    descriptor.setChildrenSortingStamp(-1);

    if (getBuilder().isAlwaysShowPlus(descriptor)) return;


    TreePath parentPath = path.getParentPath();
    if (myTree.isVisible(path) || parentPath != null && myTree.isExpanded(parentPath)) {
      if (myTree.isExpanded(path)) {
        addSubtreeToUpdate(node);
      }
      else {
        insertLoadingNode(node, false);
      }
    }
  }

  /**
   * Indicates whether the given {@code descriptor} is valid
   * and its parent is equal to the specified {@code parent}.
   *
   * @param descriptor a descriptor to test
   * @param parent     an expected parent for the testing descriptor
   * @return {@code true} if the specified descriptor is valid
   */
  private boolean isValid(NodeDescriptor<?> descriptor, NodeDescriptor<?> parent) {
    if (descriptor == null) return false;
    if (parent != null && parent != descriptor.getParentDescriptor()) return false;
    return isValid(getElementFromDescriptor(descriptor));
  }

  private boolean isValid(@Nullable NodeDescriptor<?> descriptor) {
    return descriptor != null && isValid(getElementFromDescriptor(descriptor));
  }

  private boolean isValid(Object element) {
    if (isNodeNull(element)) return false;
    if (element instanceof ValidateableNode) {
      if (!((ValidateableNode)element).isValid()) return false;
    }
    return getBuilder().validateNode(element);
  }

  private void insertLoadingNode(DefaultMutableTreeNode node, boolean addToUnbuilt) {
    if (!isLoadingChildrenFor(node)) {
      myTreeModel.insertNodeInto(new LoadingNode(), node, 0);
    }

    if (addToUnbuilt) {
      addToUnbuilt(node);
    }
  }


  @NotNull
  private Promise<Void> queueToBackground(@NotNull Runnable bgBuildAction, @Nullable Runnable edtPostRunnable) {
    if (!canInitiateNewActivity()) return Promises.rejectedPromise();
    AsyncPromise<Void> result = new AsyncPromise<>();
    AtomicReference<ProcessCanceledException> fail = new AtomicReference<>();
    Runnable finalizer = new TreeRunnable("AbstractTreeUi.queueToBackground: finalizer") {
      @Override
      public void perform() {
        ProcessCanceledException exception = fail.get();
        if (exception == null) {
          result.setResult(null);
        }
        else {
          result.setError(exception);
        }
      }
    };

    registerWorkerTask(bgBuildAction);

    Runnable pooledThreadWithProgressRunnable = new TreeRunnable("AbstractTreeUi.queueToBackground: progress") {
      @Override
      public void perform() {
        try {
          AbstractTreeBuilder builder = getBuilder();

          if (!canInitiateNewActivity()) {
            throw new ProcessCanceledException();
          }

          builder.runBackgroundLoading(new TreeRunnable("AbstractTreeUi.queueToBackground: background") {
            @Override
            public void perform() {
              assertNotDispatchThread();
              try {
                if (!canInitiateNewActivity()) {
                  throw new ProcessCanceledException();
                }

                execute(bgBuildAction);

                if (edtPostRunnable != null) {
                  builder.updateAfterLoadedInBackground(new TreeRunnable("AbstractTreeUi.queueToBackground: update after") {
                    @Override
                    public void perform() {
                      try {
                        assertIsDispatchThread();
                        if (!canInitiateNewActivity()) {
                          throw new ProcessCanceledException();
                        }

                        execute(edtPostRunnable);

                      }
                      catch (ProcessCanceledException e) {
                        fail.set(e);
                        cancelUpdate();
                      }
                      finally {
                        unregisterWorkerTask(bgBuildAction, finalizer);
                      }
                    }
                  });
                }
                else {
                  unregisterWorkerTask(bgBuildAction, finalizer);
                }
              }
              catch (ProcessCanceledException e) {
                fail.set(e);
                unregisterWorkerTask(bgBuildAction, finalizer);
                cancelUpdate();
              }
              catch (Throwable t) {
                unregisterWorkerTask(bgBuildAction, finalizer);
                throw new RuntimeException(t);
              }
            }
          });
        }
        catch (ProcessCanceledException e) {
          unregisterWorkerTask(bgBuildAction, finalizer);
          cancelUpdate();
        }
      }
    };

    Runnable pooledThreadRunnable = new TreeRunnable("AbstractTreeUi.queueToBackground") {
      @Override
      public void perform() {
        try {
          if (myProgress != null && ProgressIndicatorProvider.getGlobalProgressIndicator() != myProgress) {
            ProgressManager.getInstance().runProcess(pooledThreadWithProgressRunnable, myProgress);
          }
          else {
            execute(pooledThreadWithProgressRunnable);
          }
        }
        catch (ProcessCanceledException e) {
          fail.set(e);
          unregisterWorkerTask(bgBuildAction, finalizer);
          cancelUpdate();
        }
      }
    };

    if (isPassthroughMode()) {
      execute(pooledThreadRunnable);
    }
    else {
      myWorker.addFirst(pooledThreadRunnable);
    }
    return result;
  }

  private void registerWorkerTask(@NotNull Runnable runnable) {
    synchronized (myActiveWorkerTasks) {
      myActiveWorkerTasks.add(runnable);
    }
  }

  private void unregisterWorkerTask(@NotNull Runnable runnable, @Nullable Runnable finalizeRunnable) {
    boolean wasRemoved;

    synchronized (myActiveWorkerTasks) {
      wasRemoved = myActiveWorkerTasks.remove(runnable);
    }

    if (wasRemoved && finalizeRunnable != null) {
      finalizeRunnable.run();
    }

    invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.unregisterWorkerTask") {
      @Override
      public void perform() {
        maybeReady();
      }
    });
  }

  private boolean isWorkerBusy() {
    synchronized (myActiveWorkerTasks) {
      return !myActiveWorkerTasks.isEmpty();
    }
  }

  private void clearWorkerTasks() {
    synchronized (myActiveWorkerTasks) {
      myActiveWorkerTasks.clear();
    }
  }

  private void updateNodeImageAndPosition(@NotNull DefaultMutableTreeNode node) {
    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;
    if (getElementFromDescriptor(descriptor) == null) return;

    nodeChanged(node);
  }

  private void nodeChanged(DefaultMutableTreeNode node) {
    invokeLaterIfNeeded(true, new TreeRunnable("AbstractTreeUi.nodeChanged") {
      @Override
      public void perform() {
        myTreeModel.nodeChanged(node);
      }
    });
  }

  private void nodeStructureChanged(DefaultMutableTreeNode node) {
    invokeLaterIfNeeded(true, new TreeRunnable("AbstractTreeUi.nodeStructureChanged") {
      @Override
      public void perform() {
        myTreeModel.nodeStructureChanged(node);
      }
    });
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  private void insertNodesInto(@NotNull List<? extends TreeNode> toInsert, @NotNull DefaultMutableTreeNode parentNode) {
    sortChildren(parentNode, toInsert, false, true);
    List<TreeNode> all = new ArrayList<>(toInsert.size() + parentNode.getChildCount());
    all.addAll(toInsert);
    all.addAll(TreeUtil.listChildren(parentNode));

    if (!toInsert.isEmpty()) {
      sortChildren(parentNode, all, true, true);

      int[] newNodeIndices = new int[toInsert.size()];
      int eachNewNodeIndex = 0;
      TreeMap<Integer, TreeNode> insertSet = new TreeMap<>();
      for (int i = 0; i < toInsert.size(); i++) {
        TreeNode eachNewNode = toInsert.get(i);
        while (all.get(eachNewNodeIndex) != eachNewNode) {
          eachNewNodeIndex++;
        }
        newNodeIndices[i] = eachNewNodeIndex;
        insertSet.put(eachNewNodeIndex, eachNewNode);
      }

      for (Map.Entry<Integer, TreeNode> entry : insertSet.entrySet()) {
        TreeNode eachNode = entry.getValue();
        Integer index = entry.getKey();
        parentNode.insert((MutableTreeNode)eachNode, index);
      }

      myTreeModel.nodesWereInserted(parentNode, newNodeIndices);
    }
    else {
      List<TreeNode> before = new ArrayList<>(all);

      sortChildren(parentNode, all, true, false);
      if (!before.equals(all)) {
        processInnerChange(new TreeRunnable("AbstractTreeUi.insertNodesInto") {
          @Override
          public void perform() {
            Enumeration<TreePath> expanded = getTree().getExpandedDescendants(getPathFor(parentNode));
            TreePath[] selected = getTree().getSelectionModel().getSelectionPaths();

            parentNode.removeAllChildren();
            for (TreeNode each : all) {
              parentNode.add((MutableTreeNode)each);
            }
            nodeStructureChanged(parentNode);

            if (expanded != null) {
              while (expanded.hasMoreElements()) {
                expandSilently(expanded.nextElement());
              }
            }

            if (selected != null) {
              for (TreePath each : selected) {
                if (!getTree().getSelectionModel().isPathSelected(each)) {
                  addSelectionSilently(each);
                }
              }
            }
          }
        });
      }
    }
  }

  private void sortChildren(@NotNull DefaultMutableTreeNode node, @NotNull List<? extends TreeNode> children, boolean updateStamp, boolean forceSort) {
    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    assert descriptor != null;

    if (descriptor.getChildrenSortingStamp() >= getComparatorStamp() && !forceSort) return;
    if (!children.isEmpty()) {
      try {
        getBuilder().sortChildren(myNodeComparator, node, children);
      }
      catch (IllegalArgumentException exception) {
        StringBuilder sb = new StringBuilder("cannot sort children in ").append(this);
        children.forEach(child -> sb.append('\n').append(child));
        throw new IllegalArgumentException(sb.toString(), exception);
      }
    }

    if (updateStamp) {
      descriptor.setChildrenSortingStamp(getComparatorStamp());
    }
  }

  private void disposeNode(@NotNull DefaultMutableTreeNode node) {
    TreeNode parent = node.getParent();
    if (parent instanceof DefaultMutableTreeNode) {
      addToUnbuilt((DefaultMutableTreeNode)parent);
    }

    if (node.getChildCount() > 0) {
      for (DefaultMutableTreeNode _node = (DefaultMutableTreeNode)node.getFirstChild(); _node != null; _node = _node.getNextSibling()) {
        disposeNode(_node);
      }
    }

    removeFromUpdatingChildren(node);
    removeFromUnbuilt(node);
    removeFromCancelled(node);

    if (isLoadingNode(node)) return;
    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;
    Object element = getElementFromDescriptor(descriptor);
    if (!isNodeNull(element)) {
      removeMapping(element, node, null);
    }
    myAutoExpandRoots.remove(element);
    node.setUserObject(null);
    node.removeAllChildren();
  }

  public boolean addSubtreeToUpdate(@NotNull DefaultMutableTreeNode root) {
    return addSubtreeToUpdate(root, true);
  }

  public boolean addSubtreeToUpdate(@NotNull DefaultMutableTreeNode root, boolean updateStructure) {
    return addSubtreeToUpdate(root, null, updateStructure);
  }

  public boolean addSubtreeToUpdate(@NotNull DefaultMutableTreeNode root, Runnable runAfterUpdate) {
    return addSubtreeToUpdate(root, runAfterUpdate, true);
  }

  public boolean addSubtreeToUpdate(@NotNull DefaultMutableTreeNode root, @Nullable Runnable runAfterUpdate, boolean updateStructure) {
    Object element = getElementFor(root);
    boolean alwaysLeaf = element != null && getTreeStructure().isAlwaysLeaf(element);
    TreeUpdatePass updatePass;
    if (alwaysLeaf) {
      removeFromUnbuilt(root);
      removeLoading(root, true);
      updatePass = new TreeUpdatePass(root).setUpdateChildren(false);
    }
    else {
      updatePass = new TreeUpdatePass(root).setUpdateStructure(updateStructure).setUpdateStamp(-1);
    }
    AbstractTreeUpdater updater = getUpdater();
    updater.runAfterUpdate(runAfterUpdate);
    updater.addSubtreeToUpdate(updatePass);
    return !alwaysLeaf;
  }

  boolean wasRootNodeInitialized() {
    return myRootNodeWasQueuedToInitialize && myRootNodeInitialized;
  }

  public void select(Object @NotNull [] elements, @Nullable Runnable onDone) {
    select(elements, onDone, false);
  }

  public void select(Object @NotNull [] elements, @Nullable Runnable onDone, boolean addToSelection) {
    select(elements, onDone, addToSelection, false);
  }

  public void select(Object @NotNull [] elements, @Nullable Runnable onDone, boolean addToSelection, boolean deferred) {
    _select(elements, onDone, addToSelection, true, false, true, deferred, false, false);
  }

  void _select(Object @NotNull [] elements,
               Runnable onDone,
               boolean addToSelection,
               boolean checkIfInStructure) {

    _select(elements, onDone, addToSelection, true, checkIfInStructure, true, false, false, false);
  }

  void _select(Object @NotNull [] elements,
               @NotNull Runnable onDone) {

    _select(elements, onDone, false, true, true, false, false, false, false);
  }

  public void userSelect(Object @NotNull [] elements, Runnable onDone, boolean addToSelection, boolean scroll) {
    _select(elements, onDone, addToSelection, true, false, scroll, false, true, true);
  }

  void _select(Object @NotNull [] elements,
               Runnable onDone,
               boolean addToSelection,
               boolean checkCurrentSelection,
               boolean checkIfInStructure,
               boolean scrollToVisible,
               boolean deferred,
               boolean canSmartExpand,
               boolean mayQueue) {

    assertIsDispatchThread();

    AbstractTreeUpdater updater = getUpdater();
    if (mayQueue && updater != null) {
      updater.queueSelection(
        new SelectionRequest(elements, onDone, addToSelection, checkCurrentSelection, checkIfInStructure, scrollToVisible, deferred,
                             canSmartExpand));
      return;
    }


    boolean willAffectSelection = elements.length > 0 || addToSelection;
    if (!willAffectSelection) {
      runDone(onDone);
      maybeReady();
      return;
    }

    boolean oldCanProcessDeferredSelection = myCanProcessDeferredSelections;

    if (!deferred && wasRootNodeInitialized()) {
      _getReady().doWhenDone(new TreeRunnable("AbstractTreeUi._select: on done getReady") {
        @Override
        public void perform() {
          myCanProcessDeferredSelections = false;
        }
      });
    }

    if (!checkDeferred(deferred, onDone)) return;

    if (!deferred && oldCanProcessDeferredSelection && !myCanProcessDeferredSelections) {
      if (!addToSelection) {
        getTree().clearSelection();
      }
    }


    runDone(new TreeRunnable("AbstractTreeUi._select") {
      @Override
      public void perform() {
        try {
          if (!checkDeferred(deferred, onDone)) return;

          Set<Object> currentElements = getSelectedElements();

          if (checkCurrentSelection && !currentElements.isEmpty() && elements.length == currentElements.size()) {
            boolean runSelection = false;
            for (Object eachToSelect : elements) {
              if (!currentElements.contains(eachToSelect)) {
                runSelection = true;
                break;
              }
            }

            if (!runSelection) {
              selectVisible(elements[0], onDone, false, false, scrollToVisible);
              return;
            }
          }

          clearSelection();
          Set<Object> toSelect = new HashSet<>();
          ContainerUtil.addAllNotNull(toSelect, elements);
          if (addToSelection) {
            ContainerUtil.addAllNotNull(toSelect, currentElements);
          }

          if (checkIfInStructure) {
            toSelect.removeIf(each -> !isInStructure(each));
          }

          Object[] elementsToSelect = ArrayUtil.toObjectArray(toSelect);

          if (wasRootNodeInitialized()) {
            int[] originalRows = myTree.getSelectionRows();
            if (!addToSelection) {
              clearSelection();
            }
            addNext(elementsToSelect, 0, new TreeRunnable("AbstractTreeUi._select: addNext") {
              @Override
              public void perform() {
                if (getTree().isSelectionEmpty()) {
                  processInnerChange(new TreeRunnable("AbstractTreeUi._select: addNext: processInnerChange") {
                    @Override
                    public void perform() {
                      restoreSelection(currentElements);
                    }
                  });
                }
                runDone(onDone);
              }
            }, originalRows, deferred, scrollToVisible, canSmartExpand);
          }
          else {
            addToDeferred(elementsToSelect, onDone, addToSelection);
          }
        }
        finally {
          maybeReady();
        }
      }
    });
  }

  private void clearSelection() {
    mySelectionIsBeingAdjusted = true;
    try {
      myTree.clearSelection();
    }
    finally {
      mySelectionIsBeingAdjusted = false;
    }
  }

  boolean isSelectionBeingAdjusted() {
    return mySelectionIsBeingAdjusted;
  }

  private void restoreSelection(@NotNull Set<Object> selection) {
    for (Object each : selection) {
      DefaultMutableTreeNode node = getNodeForElement(each, false);
      if (node != null && isValidForSelectionAdjusting(node)) {
        addSelectionPath(getPathFor(node), false, null, null);
      }
    }
  }


  private void addToDeferred(Object @NotNull [] elementsToSelect, Runnable onDone, boolean addToSelection) {
    if (!addToSelection) {
      myDeferredSelections.clear();
    }
    myDeferredSelections.add(new TreeRunnable("AbstractTreeUi.addToDeferred") {
      @Override
      public void perform() {
        select(elementsToSelect, onDone, addToSelection, true);
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
    TreePath[] paths = myTree.getSelectionPaths();

    Set<Object> result = new LinkedHashSet<>();
    if (paths != null) {
      for (TreePath eachPath : paths) {
        if (eachPath.getLastPathComponent() instanceof DefaultMutableTreeNode eachNode) {
          if (eachNode == myRootNode && !myTree.isRootVisible()) continue;
          Object eachElement = getElementFor(eachNode);
          if (eachElement != null) {
            result.add(eachElement);
          }
        }
      }
    }
    return result;
  }


  private void addNext(Object @NotNull [] elements,
                       int i,
                       @Nullable Runnable onDone,
                       int[] originalRows,
                       boolean deferred,
                       boolean scrollToVisible,
                       boolean canSmartExpand) {
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

      doSelect(elements[i], new TreeRunnable("AbstractTreeUi.addNext") {
        @Override
        public void perform() {
          if (!checkDeferred(deferred, onDone)) return;

          addNext(elements, i + 1, onDone, originalRows, deferred, scrollToVisible, canSmartExpand);
        }
      }, deferred, i == 0, scrollToVisible, canSmartExpand);
    }
  }

  public void select(@Nullable Object element, @Nullable Runnable onDone) {
    select(element, onDone, false);
  }

  public void select(@Nullable Object element, @Nullable Runnable onDone, boolean addToSelection) {
     if (element == null) return;
    _select(new Object[]{element}, onDone, addToSelection, false);
  }

  private void doSelect(@NotNull Object element,
                        Runnable onDone,
                        boolean deferred,
                        boolean canBeCentered,
                        boolean scrollToVisible,
                        boolean canSmartExpand) {
    Runnable _onDone = new TreeRunnable("AbstractTreeUi.doSelect") {
      @Override
      public void perform() {
        if (!checkDeferred(deferred, onDone)) return;

        checkPathAndMaybeRevalidate(element, new TreeRunnable("AbstractTreeUi.doSelect: checkPathAndMaybeRevalidate") {
          @Override
          public void perform() {
            selectVisible(element, onDone, true, canBeCentered, scrollToVisible);
          }
        }, true, canSmartExpand);
      }
    };
    _expand(element, _onDone, true, false, canSmartExpand);
  }

  private void checkPathAndMaybeRevalidate(@NotNull Object element,
                                           @NotNull Runnable onDone,
                                           boolean parentsOnly,
                                           boolean canSmartExpand) {
    boolean toRevalidate = isValid(element) && !myRevalidatedObjects.contains(element) && getNodeForElement(element, false) == null && isInStructure(element);
    if (!toRevalidate) {
      runDone(onDone);
      return;
    }

    myRevalidatedObjects.add(element);
    getBuilder()
      .revalidateElement(element)
      .onSuccess(o -> invokeLaterIfNeeded(false, new TreeRunnable("AbstractTreeUi.checkPathAndMaybeRevalidate: on done revalidateElement") {
        @Override
        public void perform() {
          _expand(o, onDone, parentsOnly, false, canSmartExpand);
        }
      }))
      .onError(throwable -> wrapDone(onDone, "AbstractTreeUi.checkPathAndMaybeRevalidate: on rejected revalidateElement").run());
  }

  public void scrollSelectionToVisible(@Nullable Runnable onDone, boolean shouldBeCentered) {
    SwingUtilities.invokeLater(new TreeRunnable("AbstractTreeUi.scrollSelectionToVisible") {
      @Override
      public void perform() {
        if (isReleased()) return;

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
    });
  }

  private void selectVisible(@NotNull Object element, Runnable onDone, boolean addToSelection, boolean canBeCentered, boolean scroll) {
    DefaultMutableTreeNode toSelect = getNodeToScroll(element);
    if (toSelect == null) {
      runDone(onDone);
      return;
    }
    if (myUpdaterState != null) {
      myUpdaterState.addSelection(element);
    }
    setHoldSize(false);
    runDone(wrapScrollTo(onDone, element, toSelect, addToSelection, canBeCentered, scroll));
  }

  void userScrollTo(Object element, Runnable onDone) {
    DefaultMutableTreeNode node = getNodeToScroll(element);
    runDone(node == null ? onDone : wrapScrollTo(onDone, element, node, false, true, true));
  }

  private DefaultMutableTreeNode getNodeToScroll(Object element) {
    if (element == null) return null;
    DefaultMutableTreeNode node = getNodeForElement(element, false);
    if (node == null) return null;
    return myTree.isRootVisible() || node != getRootNode() ? node : null;
  }

  @NotNull
  private Runnable wrapDone(Runnable onDone, @NotNull String name) {
    return new TreeRunnable(name) {
      @Override
      public void perform() {
        runDone(onDone);
      }
    };
  }

  @NotNull
  private Runnable wrapScrollTo(Runnable onDone,
                                @NotNull Object element,
                                @NotNull DefaultMutableTreeNode node,
                                boolean addToSelection,
                                boolean canBeCentered,
                                boolean scroll) {
    return new TreeRunnable("AbstractTreeUi.wrapScrollTo") {
      @Override
      public void perform() {
        int row = getRowIfUnderSelection(element);
        if (row == -1) row = myTree.getRowForPath(new TreePath(node.getPath()));
        int top = row - 2;
        int bottom = row + 2;
        if (canBeCentered && Registry.is("ide.tree.autoscrollToVCenter")) {
          int count = TreeUtil.getVisibleRowCount(myTree) - 1;
          top = count > 0 ? row - count / 2 : row;
          bottom = count > 0 ? top + count : row;
        }
        TreeUtil.showAndSelect(myTree, top, bottom, row, -1, addToSelection, scroll)
          .doWhenDone(wrapDone(onDone, "AbstractTreeUi.wrapScrollTo.onDone"));
      }
    };
  }

  private int getRowIfUnderSelection(@NotNull Object element) {
    Set<Object> selection = getSelectedElements();

    if (selection.contains(element)) {
      TreePath[] paths = getTree().getSelectionPaths();
      for (TreePath each : paths) {
        if (element.equals(getElementFor(each.getLastPathComponent()))) {
          return getTree().getRowForPath(each);
        }
      }
      return -1;
    }

    Object anchor = TreeAnchorizer.getService().createAnchor(element);
    Object o = isNodeNull(anchor) ? null : myElementToNodeMap.get(anchor);
    TreeAnchorizer.getService().freeAnchor(anchor);

    if (o instanceof List) {
      TreePath[] paths = getTree().getSelectionPaths();
      if (paths != null && paths.length > 0) {
        Set<DefaultMutableTreeNode> selectedNodes = new HashSet<>();
        for (TreePath eachPAth : paths) {
          if (eachPAth.getLastPathComponent() instanceof DefaultMutableTreeNode) {
            selectedNodes.add((DefaultMutableTreeNode)eachPAth.getLastPathComponent());
          }
        }


        //noinspection unchecked
        for (DefaultMutableTreeNode eachNode : (List<DefaultMutableTreeNode>)o) {
          while (eachNode != null) {
            if (selectedNodes.contains(eachNode)) {
              return getTree().getRowForPath(getPathFor(eachNode));
            }
            eachNode = (DefaultMutableTreeNode)eachNode.getParent();
          }
        }
      }
    }

    return -1;
  }

  public void expandAll(@Nullable Runnable onDone) {
    JTree tree = getTree();
    if (tree.getRowCount() > 0) {
      int expandRecursionDepth = Math.max(2, Registry.intValue("ide.tree.expandRecursionDepth"));
      new TreeRunnable("AbstractTreeUi.expandAll") {
        private int myCurrentRow;
        private int myInvocationCount;

        @Override
        public void perform() {
          if (++myInvocationCount > expandRecursionDepth) {
            myInvocationCount = 0;
            if (isPassthroughMode()) {
              run();
            }
            else {
              // need this to prevent stack overflow if the tree is rather big and is "synchronous"
              SwingUtilities.invokeLater(this);
            }
          }
          else {
            int row = myCurrentRow++;
            if (row < tree.getRowCount()) {
              TreePath path = tree.getPathForRow(row);
              Object last = path.getLastPathComponent();
              Object elem = getElementFor(last);
              expand(elem, this);
            }
            else {
              runDone(onDone);
            }
          }
        }
      }.run();
    }
    else {
      runDone(onDone);
    }
  }

  public void expand(Object element, @Nullable Runnable onDone) {
    expand(new Object[]{element}, onDone);
  }

  public void expand(Object @NotNull [] element, @Nullable Runnable onDone) {
    expand(element, onDone, false);
  }


  void expand(Object @NotNull [] element, @Nullable Runnable onDone, boolean checkIfInStructure) {
    _expand(element, onDone == null ? new EmptyRunnable() : onDone, checkIfInStructure);
  }

  private void _expand(Object @NotNull [] elements,
                       @NotNull Runnable onDone,
                       boolean checkIfInStructure) {

    try {
      runDone(new TreeRunnable("AbstractTreeUi._expand") {
        @Override
        public void perform() {
          if (elements.length == 0) {
            runDone(onDone);
            return;
          }

          if (myUpdaterState != null) {
            myUpdaterState.clearExpansion();
          }


          ActionCallback done = new ActionCallback(elements.length);
          done
            .doWhenDone(wrapDone(onDone, "AbstractTreeUi._expand: on done expandNext"))
            .doWhenRejected(wrapDone(onDone, "AbstractTreeUi._expand: on rejected expandNext"));

          expandNext(elements, 0, false, checkIfInStructure, false, done, 0);
        }
      });
    }
    catch (ProcessCanceledException e) {
      try {
        runDone(onDone);
      }
      catch (ProcessCanceledException ignored) {
        //todo[kirillk] added by Nik to fix IDEA-58475. I'm not sure that it is correct solution
      }
    }
  }

  private void expandNext(Object @NotNull [] elements,
                          int index,
                          boolean parentsOnly,
                          boolean checkIfInStricture,
                          boolean canSmartExpand,
                          @NotNull ActionCallback done,
                          int currentDepth) {
    if (elements.length <= 0) {
      done.setDone();
      return;
    }

    if (index >= elements.length) {
      return;
    }

    int[] actualDepth = {currentDepth};
    boolean breakCallChain = false;
    if (actualDepth[0] > Registry.intValue("ide.tree.expandRecursionDepth")) {
      actualDepth[0] = 0;
      breakCallChain = true;
    }

    Runnable expandRunnable = new TreeRunnable("AbstractTreeUi.expandNext") {
      @Override
      public void perform() {
        _expand(elements[index], new TreeRunnable("AbstractTreeUi.expandNext: on done") {
          @Override
          public void perform() {
            done.setDone();
            expandNext(elements, index + 1, parentsOnly, checkIfInStricture, canSmartExpand, done, actualDepth[0] + 1);
          }
        }, parentsOnly, checkIfInStricture, canSmartExpand);
      }
    };

    if (breakCallChain && !isPassthroughMode()) {
      SwingUtilities.invokeLater(expandRunnable);
    }
    else {
      expandRunnable.run();
    }
  }

  public void collapseChildren(@NotNull Object element, @Nullable Runnable onDone) {
    runDone(new TreeRunnable("AbstractTreeUi.collapseChildren") {
      @Override
      public void perform() {
        DefaultMutableTreeNode node = getNodeForElement(element, false);
        if (node != null) {
          getTree().collapsePath(new TreePath(node.getPath()));
          runDone(onDone);
        }
      }
    });
  }

  private void runDone(@Nullable Runnable done) {
    if (done == null) return;

    if (!canInitiateNewActivity()) {
      if (done instanceof AbstractTreeBuilder.UserRunnable) {
        return;
      }
    }

    if (isYieldingNow()) {
      myYieldingDoneRunnables.add(done);
    }
    else {
      try {
        execute(done);
      }
      catch (ProcessCanceledException ignored) {
      }
    }
  }

  private void _expand(Object element,
                       @NotNull Runnable onDone,
                       boolean parentsOnly,
                       boolean checkIfInStructure,
                       boolean canSmartExpand) {

    if (checkIfInStructure && !isInStructure(element)) {
      runDone(onDone);
      return;
    }

    if (wasRootNodeInitialized()) {
      List<Object> kidsToExpand = new ArrayList<>();
      Object eachElement = element;
      DefaultMutableTreeNode firstVisible = null;
      while (true) {
        if (eachElement == null || !isValid(eachElement)) break;

        int preselected = getRowIfUnderSelection(eachElement);
        if (preselected >= 0) {
          firstVisible = (DefaultMutableTreeNode)getTree().getPathForRow(preselected).getLastPathComponent();
        }
        else {
          firstVisible = getNodeForElement(eachElement, true);
        }


        if (eachElement != element || !parentsOnly) {
          kidsToExpand.add(eachElement);
        }
        if (firstVisible != null) break;
        eachElement = getTreeStructure().getParentElement(eachElement);
        if (eachElement == null) break;

        int i = kidsToExpand.indexOf(eachElement);
        if (i != -1) {
          try {
            Object existing = kidsToExpand.get(i);
            LOG.error("Tree path contains equal elements at different levels:\n" +
                      " element: '" + eachElement + "'; " + eachElement.getClass() + " ("+System.identityHashCode(eachElement)+");\n" +
                      "existing: '" + existing + "'; " + existing.getClass()+ " ("+System.identityHashCode(existing)+"); " +
                      "path='" + kidsToExpand + "'; tree structure=" + myTreeStructure);
          }
          catch (AssertionError ignored) {
          }
          runDone(onDone);
          throw new ProcessCanceledException();
        }
      }


      if (firstVisible == null) {
        runDone(onDone);
      }
      else if (kidsToExpand.isEmpty()) {
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)firstVisible.getParent();
        if (parentNode != null) {
          TreePath parentPath = new TreePath(parentNode.getPath());
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

  private void deferExpansion(Object element, @NotNull Runnable onDone, boolean parentsOnly, boolean canSmartExpand) {
    myDeferredExpansions.add(new TreeRunnable("AbstractTreeUi.deferExpansion") {
      @Override
      public void perform() {
        _expand(element, onDone, parentsOnly, false, canSmartExpand);
      }
    });
  }

  private void processExpand(DefaultMutableTreeNode toExpand,
                             @NotNull List<Object> kidsToExpand,
                             int expandIndex,
                             @NotNull Runnable onDone,
                             boolean canSmartExpand) {

    Object element = getElementFor(toExpand);
    if (element == null) {
      runDone(onDone);
      return;
    }

    addNodeAction(element, true, node -> {
      if (node.getChildCount() > 0 && !myTree.isExpanded(new TreePath(node.getPath()))) {
        if (!isAutoExpand(node)) {
          expand(node, canSmartExpand);
        }
      }

      if (expandIndex <= 0) {
        runDone(onDone);
        return;
      }

      checkPathAndMaybeRevalidate(kidsToExpand.get(expandIndex - 1), new TreeRunnable("AbstractTreeUi.processExpand") {
        @Override
        public void perform() {
          DefaultMutableTreeNode nextNode = getNodeForElement(kidsToExpand.get(expandIndex - 1), false);
          processExpand(nextNode, kidsToExpand, expandIndex - 1, onDone, canSmartExpand);
        }
      }, false, canSmartExpand);
    });


    boolean childrenToUpdate = areChildrenToBeUpdated(toExpand);
    boolean expanded = myTree.isExpanded(getPathFor(toExpand));
    boolean unbuilt = myUnbuiltNodes.contains(toExpand);

    if (expanded) {
      if (unbuilt || childrenToUpdate) {
        addSubtreeToUpdate(toExpand);
      }
    }
    else {
      expand(toExpand, canSmartExpand);
    }

    if (!unbuilt && !childrenToUpdate) {
      processNodeActionsIfReady(toExpand);
    }
  }

  private boolean areChildrenToBeUpdated(DefaultMutableTreeNode node) {
    return getUpdater().isEnqueuedToUpdate(node) || isUpdatingParent(node) || myCancelledBuild.containsKey(node);
  }

  @Nullable
  public Object getElementFor(Object node) {
    NodeDescriptor<?> descriptor = getDescriptorFrom(node);
    return descriptor == null ? null : getElementFromDescriptor(descriptor);
  }

  final boolean isNodeBeingBuilt(@NotNull TreePath path) {
    return isNodeBeingBuilt(path.getLastPathComponent());
  }

  private boolean isNodeBeingBuilt(@NotNull Object node) {
    return getParentBuiltNode(node) != null || myRootNode == node && !wasRootNodeInitialized();
  }

  @Nullable
  private DefaultMutableTreeNode getParentBuiltNode(@NotNull Object node) {
    DefaultMutableTreeNode parent = getParentLoadingInBackground(node);
    if (parent != null) return parent;

    if (isLoadingParentInBackground(node)) return (DefaultMutableTreeNode)node;

    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;

    boolean childrenAreNoLoadedYet = myUnbuiltNodes.contains(treeNode) || isUpdatingChildrenNow(treeNode);
    if (childrenAreNoLoadedYet) {
      TreePath nodePath = new TreePath(treeNode.getPath());
      if (!myTree.isExpanded(nodePath)) return null;

      return (DefaultMutableTreeNode)node;
    }


    return null;
  }

  private boolean isLoadingParentInBackground(Object node) {
    return node instanceof DefaultMutableTreeNode && isLoadedInBackground(getElementFor(node));
  }

  public void setTreeStructure(@NotNull AbstractTreeStructure treeStructure) {
    myTreeStructure = treeStructure;
    clearUpdaterState();
  }

  public AbstractTreeUpdater getUpdater() {
    return myUpdater;
  }

  public void setUpdater(@Nullable AbstractTreeUpdater updater) {
    myUpdater = updater;
    if (updater != null && myUpdateIfInactive) {
      updater.showNotify();
    }

    if (myUpdater != null) {
      myUpdater.setPassThroughMode(myPassThroughMode);
    }
  }

  public DefaultMutableTreeNode getRootNode() {
    return myRootNode;
  }

  public void setRootNode(@NotNull DefaultMutableTreeNode rootNode) {
    myRootNode = rootNode;
  }

  private void dropUpdaterStateIfExternalChange() {
    if (!isInnerChange()) {
      clearUpdaterState();
      myAutoExpandRoots.clear();
      mySelectionIsAdjusted = false;
    }
  }

  void clearUpdaterState() {
    myUpdaterState = null;
  }

  private void createMapping(@NotNull Object element, DefaultMutableTreeNode node) {
    element = TreeAnchorizer.getService().createAnchor(element);
    warnMap("myElementToNodeMap: createMapping: ", myElementToNodeMap);
    if (!myElementToNodeMap.containsKey(element)) {
      myElementToNodeMap.put(element, node);
    }
    else {
      Object value = myElementToNodeMap.get(element);
      List<DefaultMutableTreeNode> nodes;
      if (value instanceof DefaultMutableTreeNode) {
        nodes = new ArrayList<>();
        nodes.add((DefaultMutableTreeNode)value);
        myElementToNodeMap.put(element, nodes);
      }
      else {
        //noinspection unchecked
        nodes = (List<DefaultMutableTreeNode>)value;
      }
      nodes.add(node);
    }
  }

  private void removeMapping(@NotNull Object element, DefaultMutableTreeNode node, @Nullable Object elementToPutNodeActionsFor) {
    element = TreeAnchorizer.getService().createAnchor(element);
    warnMap("myElementToNodeMap: removeMapping: ", myElementToNodeMap);
    Object value = myElementToNodeMap.get(element);
    if (value != null) {
      if (value instanceof DefaultMutableTreeNode) {
        if (value.equals(node)) {
          myElementToNodeMap.remove(element);
        }
      }
      else {
        //noinspection unchecked
        List<DefaultMutableTreeNode> nodes = (List<DefaultMutableTreeNode>)value;
        boolean reallyRemoved = nodes.remove(node);
        if (reallyRemoved) {
          if (nodes.isEmpty()) {
            myElementToNodeMap.remove(element);
          }
        }
      }
    }

    remapNodeActions(element, elementToPutNodeActionsFor);
    TreeAnchorizer.getService().freeAnchor(element);
  }

  private void remapNodeActions(Object element, Object elementToPutNodeActionsFor) {
    _remapNodeActions(element, elementToPutNodeActionsFor, myNodeActions);
    _remapNodeActions(element, elementToPutNodeActionsFor, myNodeChildrenActions);
    warnMap("myNodeActions: remapNodeActions: ", myNodeActions);
    warnMap("myNodeChildrenActions: remapNodeActions: ", myNodeChildrenActions);
  }

  private static void _remapNodeActions(Object element, @Nullable Object elementToPutNodeActionsFor, @NotNull Map<Object, List<NodeAction>> nodeActions) {
    List<NodeAction> actions = nodeActions.get(element);
    nodeActions.remove(element);

    if (elementToPutNodeActionsFor != null && actions != null) {
      nodeActions.put(elementToPutNodeActionsFor, actions);
    }
  }

  @Nullable
  private DefaultMutableTreeNode getFirstNode(@NotNull Object element) {
    return findNode(element, 0);
  }

  @Nullable
  private DefaultMutableTreeNode findNode(@NotNull Object element, int startIndex) {
    Object value = getBuilder().findNodeByElement(element);
    if (value == null) {
      return null;
    }
    if (value instanceof DefaultMutableTreeNode) {
      return startIndex == 0 ? (DefaultMutableTreeNode)value : null;
    }
    //noinspection unchecked
    List<DefaultMutableTreeNode> nodes = (List<DefaultMutableTreeNode>)value;
    return startIndex < nodes.size() ? nodes.get(startIndex) : null;
  }

  Object findNodeByElement(Object element) {
    element = TreeAnchorizer.getService().createAnchor(element);
    try {
      if (isNodeNull(element)) return null;
      if (myElementToNodeMap.containsKey(element)) {
        return myElementToNodeMap.get(element);
      }

      TREE_NODE_WRAPPER.setValue(element);
      return myElementToNodeMap.get(TREE_NODE_WRAPPER);
    }
    finally {
      TREE_NODE_WRAPPER.setValue(null);
      TreeAnchorizer.getService().freeAnchor(element);
    }
  }

  @Nullable
  private DefaultMutableTreeNode findNodeForChildElement(@NotNull DefaultMutableTreeNode parentNode, Object element) {
    Object anchor = TreeAnchorizer.getService().createAnchor(element);
    Object value = isNodeNull(anchor) ? null : myElementToNodeMap.get(anchor);
    TreeAnchorizer.getService().freeAnchor(anchor);
    if (value == null) {
      return null;
    }

    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode elementNode = (DefaultMutableTreeNode)value;
      return parentNode.equals(elementNode.getParent()) ? elementNode : null;
    }

    //noinspection unchecked
    List<DefaultMutableTreeNode> allNodesForElement = (List<DefaultMutableTreeNode>)value;
    for (DefaultMutableTreeNode elementNode : allNodesForElement) {
      if (parentNode.equals(elementNode.getParent())) {
        return elementNode;
      }
    }

    return null;
  }

  private void addNodeAction(Object element, boolean shouldChildrenBeReady, @NotNull NodeAction action) {
    _addNodeAction(element, action, myNodeActions);
    if (shouldChildrenBeReady) {
      _addNodeAction(element, action, myNodeChildrenActions);
    }
    warnMap("myNodeActions: addNodeAction: ", myNodeActions);
    warnMap("myNodeChildrenActions: addNodeAction: ", myNodeChildrenActions);
  }


  public void addActivity() {
    if (myActivityMonitor != null) {
      myActivityMonitor.addActivity(myActivityId, getUpdater().getModalityState());
    }
  }

  private void removeActivity() {
    if (myActivityMonitor != null) {
      myActivityMonitor.removeActivity(myActivityId);
    }
  }

  private void _addNodeAction(Object element, NodeAction action, @NotNull Map<Object, List<NodeAction>> map) {
    maybeSetBusyAndScheduleWaiterForReady(true, element);
    map.computeIfAbsent(element, k -> new ArrayList<>()).add(action);

    addActivity();
  }


  private void cleanUpNow() {
    if (!canInitiateNewActivity()) return;

    UpdaterTreeState state = new UpdaterTreeState(this);

    myTree.collapsePath(new TreePath(myTree.getModel().getRoot()));
    clearSelection();
    getRootNode().removeAllChildren();
    TREE_NODE_WRAPPER = AbstractTreeBuilder.createSearchingTreeNodeWrapper();

    myRootNodeWasQueuedToInitialize = false;
    myRootNodeInitialized = false;

    clearNodeActions();
    myElementToNodeMap.clear();
    myDeferredSelections.clear();
    myDeferredExpansions.clear();
    myLoadedInBackground.clear();
    myUnbuiltNodes.clear();
    myUpdateFromRootRequested = true;

    myWorker.clear();
    myTree.invalidate();

    state.restore(null);
  }

  public void setClearOnHideDelay(long clearOnHideDelay) {
    myClearOnHideDelay = clearOnHideDelay;
  }

  private class MySelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
      if (mySilentSelect != null && mySilentSelect.equals(e.getNewLeadSelectionPath())) return;

      dropUpdaterStateIfExternalChange();
    }
  }


  private class MyExpansionListener implements TreeExpansionListener {
    @Override
    public void treeExpanded(@NotNull TreeExpansionEvent event) {
      TreePath path = event.getPath();

      if (mySilentExpand != null && mySilentExpand.equals(path)) return;

      dropUpdaterStateIfExternalChange();

      if (myRequestedExpand != null && !myRequestedExpand.equals(path)) {
        _getReady().doWhenDone(new TreeRunnable("AbstractTreeUi.MyExpansionListener.treeExpanded") {
          @Override
          public void perform() {
            Object element = getElementFor(path.getLastPathComponent());
            expand(element, null);
          }
        });
        return;
      }


      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();

      if (!myUnbuiltNodes.contains(node)) {
        removeLoading(node, false);

        Set<DefaultMutableTreeNode> childrenToUpdate = new HashSet<>();
        for (int i = 0; i < node.getChildCount(); i++) {
          DefaultMutableTreeNode each = (DefaultMutableTreeNode)node.getChildAt(i);
          if (myUnbuiltNodes.contains(each)) {
            makeLoadingOrLeafIfNoChildren(each);
            childrenToUpdate.add(each);
          }
        }

        if (!childrenToUpdate.isEmpty()) {
          for (DefaultMutableTreeNode each : childrenToUpdate) {
            maybeUpdateSubtreeToUpdate(each);
          }
        }
      }
      else {
        getBuilder().expandNodeChildren(node);
      }

      processSmartExpand(node, canSmartExpand(node, true), false);
      processNodeActionsIfReady(node);
    }

    @Override
    public void treeCollapsed(@NotNull TreeExpansionEvent e) {
      dropUpdaterStateIfExternalChange();

      TreePath path = e.getPath();
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor<?> descriptor = getDescriptorFrom(node);
      if (descriptor == null) return;

      TreePath pathToSelect = null;
      if (isSelectionInside(node)) {
        pathToSelect = new TreePath(node.getPath());
      }

      if (getBuilder().isDisposeOnCollapsing(descriptor)) {
        runDone(new TreeRunnable("AbstractTreeUi.MyExpansionListener.treeCollapsed") {
          @Override
          public void perform() {
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
        addSelectionPath(pathToSelect, true, Conditions.alwaysFalse(), null);
      }
    }
  }

  private void removeChildren(@NotNull DefaultMutableTreeNode node) {
    @SuppressWarnings({"unchecked", "rawtypes"})
    Enumeration<DefaultMutableTreeNode> children = (Enumeration)node.children();
    for (DefaultMutableTreeNode child : Collections.list(children)) {
      disposeNode(child);
    }
    node.removeAllChildren();
    nodeStructureChanged(node);
  }

  private void maybeUpdateSubtreeToUpdate(@NotNull DefaultMutableTreeNode subtreeRoot) {
    if (!myUnbuiltNodes.contains(subtreeRoot)) return;
    TreePath path = getPathFor(subtreeRoot);

    if (myTree.getRowForPath(path) == -1) return;

    DefaultMutableTreeNode parent = getParentBuiltNode(subtreeRoot);
    if (parent == null) {
      if (!getBuilder().isAlwaysShowPlus(getDescriptorFrom(subtreeRoot))) {
        addSubtreeToUpdate(subtreeRoot);
      }
    }
    else if (parent != subtreeRoot) {
      addNodeAction(getElementFor(subtreeRoot), true, parent1 -> maybeUpdateSubtreeToUpdate(subtreeRoot));
    }
  }

  private boolean isSelectionInside(@NotNull DefaultMutableTreeNode parent) {
    TreePath path = new TreePath(myTreeModel.getPathToRoot(parent));
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return false;
    for (TreePath path1 : paths) {
      if (path.isDescendant(path1)) return true;
    }
    return false;
  }

  boolean isInStructure(@Nullable Object element) {
    if (isNodeNull(element)) return false;
    AbstractTreeStructure structure = getTreeStructure();
    if (structure == null) return false;

    Object rootElement = structure.getRootElement();
    Object eachParent = element;
    while (eachParent != null) {
      if (Comparing.equal(rootElement, eachParent)) return true;
      eachParent = structure.getParentElement(eachParent);
    }

    return false;
  }

  @FunctionalInterface
  interface NodeAction {
    void onReady(@NotNull DefaultMutableTreeNode node);
  }

  void setCanYield(boolean canYield) {
    myCanYield = canYield;
  }

  @NotNull
  Collection<TreeUpdatePass> getYeildingPasses() {
    return myYieldingPasses;
  }

  private static class LoadedChildren {
    @NotNull private final List<Object> myElements;
    private final Map<Object, NodeDescriptor<?>> myDescriptors = new HashMap<>();
    private final Map<NodeDescriptor<?>, Boolean> myChanges = new HashMap<>();

    LoadedChildren(Object @Nullable [] elements) {
      myElements = Arrays.asList(elements != null ? elements : ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }

    void putDescriptor(Object element, NodeDescriptor<?> descriptor, boolean isChanged) {
      if (isUnitTestingMode()) {
        assert myElements.contains(element);
      }
      myDescriptors.put(element, descriptor);
      myChanges.put(descriptor, isChanged);
    }

    @NotNull
    List<Object> getElements() {
      return myElements;
    }

    NodeDescriptor<?> getDescriptor(Object element) {
      return myDescriptors.get(element);
    }

    @NotNull
    @Override
    public String toString() {
      return myElements + "->" + myChanges;
    }

    public boolean isUpdated(Object element) {
      NodeDescriptor<?> desc = getDescriptor(element);
      return myChanges.get(desc);
    }
  }

  private long getComparatorStamp() {
    if (myNodeDescriptorComparator instanceof NodeDescriptor.NodeComparator) {
      long currentComparatorStamp = ((NodeDescriptor.NodeComparator<?>)myNodeDescriptorComparator).getStamp();
      if (currentComparatorStamp > myLastComparatorStamp) {
        myOwnComparatorStamp = Math.max(myOwnComparatorStamp, currentComparatorStamp) + 1;
      }
      myLastComparatorStamp = currentComparatorStamp;

      return Math.max(currentComparatorStamp, myOwnComparatorStamp);
    }
    else {
      return myOwnComparatorStamp;
    }
  }

  void incComparatorStamp() {
    myOwnComparatorStamp = getComparatorStamp() + 1;
  }

  private static class UpdateInfo {
    NodeDescriptor<?> myDescriptor;
    TreeUpdatePass myPass;
    boolean myCanSmartExpand;
    boolean myWasExpanded;
    boolean myForceUpdate;
    boolean myDescriptorIsUpToDate;
    boolean myUpdateChildren;

    UpdateInfo(NodeDescriptor<?> descriptor,
               TreeUpdatePass pass,
               boolean canSmartExpand,
               boolean wasExpanded,
               boolean forceUpdate,
               boolean descriptorIsUpToDate,
               boolean updateChildren) {
      myDescriptor = descriptor;
      myPass = pass;
      myCanSmartExpand = canSmartExpand;
      myWasExpanded = wasExpanded;
      myForceUpdate = forceUpdate;
      myDescriptorIsUpToDate = descriptorIsUpToDate;
      myUpdateChildren = updateChildren;
    }

    synchronized NodeDescriptor<?> getDescriptor() {
      return myDescriptor;
    }

    synchronized TreeUpdatePass getPass() {
      return myPass;
    }

    synchronized boolean isCanSmartExpand() {
      return myCanSmartExpand;
    }

    synchronized boolean isWasExpanded() {
      return myWasExpanded;
    }

    synchronized boolean isForceUpdate() {
      return myForceUpdate;
    }

    synchronized boolean isDescriptorIsUpToDate() {
      return myDescriptorIsUpToDate;
    }

    public synchronized void apply(@NotNull UpdateInfo updateInfo) {
      myDescriptor = updateInfo.myDescriptor;
      myPass = updateInfo.myPass;
      myCanSmartExpand = updateInfo.myCanSmartExpand;
      myWasExpanded = updateInfo.myWasExpanded;
      myForceUpdate = updateInfo.myForceUpdate;
      myDescriptorIsUpToDate = updateInfo.myDescriptorIsUpToDate;
    }

    public synchronized  boolean isUpdateChildren() {
      return myUpdateChildren;
    }

    @Override
    @NotNull
    @NonNls
    public synchronized String toString() {
      return "UpdateInfo: desc=" +
             myDescriptor +
             " pass=" +
             myPass +
             " canSmartExpand=" +
             myCanSmartExpand +
             " wasExpanded=" +
             myWasExpanded +
             " forceUpdate=" +
             myForceUpdate +
             " descriptorUpToDate=" +
             myDescriptorIsUpToDate;
    }
  }


  void setPassthroughMode(boolean passthrough) {
    myPassThroughMode = passthrough;
    AbstractTreeUpdater updater = getUpdater();

    if (updater != null) {
      updater.setPassThroughMode(myPassThroughMode);
    }

    if (!isUnitTestingMode() && passthrough) {
      // TODO: this assertion should be restored back as soon as possible [JamTreeTableView should be rewritten, etc]
      //LOG.error("Pass-through mode for TreeUi is allowed only for unit test mode");
    }
  }

  boolean isPassthroughMode() {
    return myPassThroughMode;
  }

  private static boolean isUnitTestingMode() {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  private void addModelListenerToDiagnoseAccessOutsideEdt() {
    myTreeModel.addTreeModelListener(new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        assertIsDispatchThread();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        assertIsDispatchThread();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        assertIsDispatchThread();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        assertIsDispatchThread();
      }
    });
  }

  private <V> void warnMap(String prefix, Map<Object, V> map) {
    if (!LOG.isDebugEnabled()) return;
    if (!SwingUtilities.isEventDispatchThread() && !myPassThroughMode) {
      LOG.warn(prefix + "modified on wrong thread");
    }
    long count = map.keySet().stream().filter(AbstractTreeUi::isNodeNull).count();
    if (count > 0) LOG.warn(prefix + "null keys: " + count + " / " + map.size());
  }

  /**
   * @param element an element in the tree structure
   * @return {@code true} if element is {@code null} or if it contains a {@code null} value
   */
  private static boolean isNodeNull(Object element) {
    if (element instanceof AbstractTreeNode<?> node) {
      element = node.getValue();
    }
    return element == null;
  }

  public final boolean isConsistent() {
    return myTree != null && myTreeModel != null && myTreeModel == myTree.getModel();
  }
}