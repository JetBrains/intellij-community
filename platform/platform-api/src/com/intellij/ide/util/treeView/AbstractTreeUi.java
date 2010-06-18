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
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Time;
import com.intellij.util.concurrency.ReadWriteLock;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import com.intellij.util.concurrency.Sync;
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
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
  long myOwnComparatorStamp;
  long myLastComparatorStamp;

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

  private final Map<Object, UpdateInfo> myLoadedInBackground = new HashMap<Object, UpdateInfo>();
  private final Map<Object, List<NodeAction>> myNodeChildrenActions = new HashMap<Object, List<NodeAction>>();

  private long myClearOnHideDelay = -1;
  private final Map<AbstractTreeUi, Long> ourUi2Countdown = Collections.synchronizedMap(new WeakHashMap<AbstractTreeUi, Long>());

  private final Set<Runnable> myDeferredSelections = new HashSet<Runnable>();
  private final Set<Runnable> myDeferredExpansions = new HashSet<Runnable>();

  private boolean myCanProcessDeferredSelections;

  private UpdaterTreeState myUpdaterState;
  private AbstractTreeBuilder myBuilder;

  private final Set<DefaultMutableTreeNode> myUpdatingChildren = new HashSet<DefaultMutableTreeNode>();
  private long myJanitorPollPeriod = Time.SECOND * 10;

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
  private final FocusAdapter myFocusListener = new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
      maybeReady();
    }
  };
  private final Set<DefaultMutableTreeNode> myNotForSmartExpand = new HashSet<DefaultMutableTreeNode>();
  private TreePath myRequestedExpand;

  private TreePath mySilentExpand;
  private TreePath mySilentSelect;

  private final ActionCallback myInitialized = new ActionCallback();
  private BusyObject.Impl myBusyObject = new BusyObject.Impl() {
    @Override
    protected boolean isReady() {
      return AbstractTreeUi.this.isReady();
    }
  };

  private boolean myPassthroughMode = false;


  private final Set<Object> myAutoExpandRoots = new HashSet<Object>();
  private final RegistryValue myAutoExpandDepth = Registry.get("ide.tree.autoExpandMaxDepth");

  private final Set<DefaultMutableTreeNode> myWillBeExpaned = new HashSet<DefaultMutableTreeNode>();
  private SimpleTimerTask myCleanupTask;

  private AtomicBoolean myCancelRequest = new AtomicBoolean();
  private ReadWriteLock myStateLock = new ReentrantWriterPreferenceReadWriteLock();

  private AtomicBoolean myResettingToReadyNow = new AtomicBoolean();

  private Map<Progressive, ProgressIndicator> myBatchIndicators = new HashMap<Progressive, ProgressIndicator>();
  private Map<Progressive, ActionCallback> myBatchCallbacks = new HashMap<Progressive, ActionCallback>();

  private Map<DefaultMutableTreeNode, DefaultMutableTreeNode> myCancelledBuild = new WeakHashMap<DefaultMutableTreeNode, DefaultMutableTreeNode>();

  private boolean mySelectionIsAdjusted;
  private boolean myReleaseRequested;

  private Set<Object> myRevalidatedObjects = new HashSet<Object>();

  protected void init(AbstractTreeBuilder builder,
                      JTree tree,
                      DefaultTreeModel treeModel,
                      AbstractTreeStructure treeStructure,
                      @Nullable Comparator<NodeDescriptor> comparator,
                      boolean updateIfInactive) {
    myBuilder = builder;
    myTree = tree;
    myTreeModel = treeModel;
    addModelListenerToDianoseAccessOutsideEdt();
    TREE_NODE_WRAPPER = getBuilder().createSearchingTreeNodeWrapper();
    myTree.setModel(myTreeModel);
    setRootNode((DefaultMutableTreeNode)treeModel.getRoot());
    setTreeStructure(treeStructure);
    myNodeDescriptorComparator = comparator;
    myUpdateIfInactive = updateIfInactive;

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (!myRootNodeWasInitialized) {
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

    final UiNotifyConnector uiNotify = new UiNotifyConnector(tree, new Activatable() {
      public void showNotify() {
        myShowing = true;
        myWasEverShown = true;
        if (canInitiateNewActivity()) {
          activate(true);
        }
      }

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


  boolean isNodeActionsPending() {
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

  private void setHoldSize(boolean holdSize) {
    if (myTree instanceof com.intellij.ui.treeStructure.Tree) {
      final com.intellij.ui.treeStructure.Tree tree = (Tree)myTree;
      tree.setHoldSize(holdSize);
    }
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
        Runnable runnable = new Runnable() {
          public void run() {
            if (!canInitiateNewActivity()) return;

            myCleanupTask = null;
            getBuilder().cleanUp();
          }
        };
        if (isPassthroughMode()) {
          runnable.run();
        }
        else {
          UIUtil.invokeLaterIfNeeded(runnable);
        }
      }
    }
  }

  protected void doCleanUp() {
    Runnable cleanup = new Runnable() {
      public void run() {
        if (canInitiateNewActivity()) {
          cleanUpNow();
        }
      }
    };

    if (isPassthroughMode()) {
      cleanup.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(cleanup);
    }
  }

  private ActionCallback invokeLaterIfNeeded(@NotNull final Runnable runnable) {
    final ActionCallback result = new ActionCallback();

    Runnable actual = new Runnable() {
      public void run() {
        runnable.run();
        result.setDone();
      }
    };

    if (isPassthroughMode() || (!isEdt() && (!isTreeShowing() && !myWasEverShown))) {
      actual.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(actual);
    }

    return result;
  }

  public void activate(boolean byShowing) {
    cancelCurrentCleanupTask();

    myCanProcessDeferredSelections = true;
    ourUi2Countdown.remove(this);

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

  public void deactivate() {
    getUpdater().hideNotify();
    myBusyAlarm.cancelAllRequests();

    if (!myWasEverShown) return;

    if (!isReady()) {
      cancelUpdate();
      myUpdateFromRootRequested = true;
    }

    if (getClearOnHideDelay() >= 0) {
      ourUi2Countdown.put(this, System.currentTimeMillis() + getClearOnHideDelay());
      sheduleCleanUpAll();
    }
  }

  private void sheduleCleanUpAll() {
    cancelCurrentCleanupTask();

    myCleanupTask = SimpleTimer.getInstance().setUp(new Runnable() {
      public void run() {
        cleanUpAll();
      }
    }, getClearOnHideDelay());
  }

  public void requestRelease() {
    myReleaseRequested = true;
    cancelUpdate().doWhenDone(new Runnable() {
      public void run() {
        releaseNow();
      }
    });
  }

  private void releaseNow() {
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

    cancelCurrentCleanupTask();

    myTree = null;
    setUpdater(null);
    myWorker = null;
    myTreeStructure = null;
    myBuilder.releaseUi();
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
    if (!myUnbuiltNodes.contains(node)) return;
    if (isLoadedInBackground(getElementFor(node))) return;

    getTreeStructure().commit();
    addSubtreeToUpdate(node);
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
    myLastComparatorStamp = -1;
    getBuilder().queueUpdateFrom(getTreeStructure().getRootElement(), true);
  }

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

  private boolean initRootNodeNowIfNeeded(final TreeUpdatePass pass) {
    boolean wasCleanedUp = false;
    if (myRootNodeWasInitialized) {
      Object root = getTreeStructure().getRootElement();
      assert root != null : "Root element cannot be null";

      Object currentRoot = getElementFor(myRootNode);

      if (Comparing.equal(root, currentRoot)) return false;

      Object rootAgain = getTreeStructure().getRootElement();
      if (root != rootAgain && !root.equals(rootAgain)) {
        assert false : "getRootElement() if called twice must return either root1 == root2 or root1.equals(root2)";
      }

      cleanUpNow();
      wasCleanedUp = true;
    }

    myRootNodeWasInitialized = true;

    final Object rootElement = getTreeStructure().getRootElement();
    addNodeAction(rootElement, new NodeAction() {
      public void onReady(final DefaultMutableTreeNode node) {
        processDeferredActions();
      }
    }, false);


    final Ref<NodeDescriptor> rootDescriptor = new Ref<NodeDescriptor>(null);
    final boolean bgLoading = getTreeStructure().isToBuildChildrenInBackground(rootElement);

    Runnable build = new Runnable() {
      public void run() {
        rootDescriptor.set(getTreeStructure().createDescriptor(rootElement, null));
        getRootNode().setUserObject(rootDescriptor.get());
        update(rootDescriptor.get(), true);
      }
    };


    Runnable update = new Runnable() {
      public void run() {
        if (getElementFromDescriptor(rootDescriptor.get()) != null) {
          createMapping(getElementFromDescriptor(rootDescriptor.get()), getRootNode());
        }


        insertLoadingNode(getRootNode(), true);

        boolean willUpdate = false;
        if (isAutoExpand(rootDescriptor.get())) {
          willUpdate = myUnbuiltNodes.contains(getRootNode());
          expand(getRootNode(), true);
        }
        if (!willUpdate) {
          updateNodeChildren(getRootNode(), pass, null, false, false, false, true);
        }
        if (getRootNode().getChildCount() == 0) {
          myTreeModel.nodeChanged(getRootNode());
        }
      }
    };

    if (bgLoading) {
      queueToBackground(build, update, rootDescriptor);
    }
    else {
      build.run();
      update.run();
    }

    return wasCleanedUp;
  }

  private boolean isAutoExpand(NodeDescriptor descriptor) {
    return isAutoExpand(descriptor, true);
  }

  private boolean isAutoExpand(NodeDescriptor descriptor, boolean validate) {
    if (descriptor == null) return false;

    boolean autoExpand = getBuilder().isAutoExpandNode(descriptor);

    Object element = getElementFromDescriptor(descriptor);
    if (validate) {
      autoExpand = validateAutoExpand(autoExpand, element);
    }

    if (!autoExpand && !myTree.isRootVisible()) {
      if (element != null && element.equals(getTreeStructure().getRootElement())) return true;
    }

    return autoExpand;
  }

  private boolean validateAutoExpand(boolean autoExpand, Object element) {
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
        if (isInVisibleAutoExpandChain(node)) {
          autoExpand = true;
        }
        else {
          autoExpand = false;
        }
      }
    }
    return autoExpand;
  }

  private boolean isInVisibleAutoExpandChain(DefaultMutableTreeNode child) {
    TreeNode eachParent = child;
    while (eachParent != null) {

      if (myRootNode == eachParent) return true;

      NodeDescriptor eachDescriptor = getDescriptorFrom((DefaultMutableTreeNode)eachParent);
      if (!isAutoExpand(eachDescriptor, false)) {
        TreePath path = getPathFor(eachParent);
        if (myWillBeExpaned.contains(path.getLastPathComponent()) || (myTree.isExpanded(path) && myTree.isVisible(path))) {
          return true;
        }
        else {
          return false;
        }
      }
      eachParent = eachParent.getParent();
    }

    return false;
  }

  private int getDistanceToAutoExpandRoot(Object element) {
    int distance = 0;

    Object eachParent = element;
    while (eachParent != null) {
      if (myAutoExpandRoots.contains(eachParent)) break;
      eachParent = getTreeStructure().getParentElement(eachParent);
      distance++;
    }

    return eachParent != null ? distance : -1;
  }

  private boolean isAutoExpand(DefaultMutableTreeNode node) {
    return isAutoExpand(getDescriptorFrom(node));
  }

  private AsyncResult<Boolean> update(final NodeDescriptor nodeDescriptor, boolean now) {
    final AsyncResult<Boolean> result = new AsyncResult<Boolean>();

    if (now || isPassthroughMode()) {
      return new AsyncResult<Boolean>().setDone(_update(nodeDescriptor));
    }

    Object element = getElementFromDescriptor(nodeDescriptor);
    boolean bgLoading = getTreeStructure().isToBuildChildrenInBackground(element);

    boolean edt = isEdt();
    if (bgLoading) {
      if (edt) {
        final Ref<Boolean> changes = new Ref<Boolean>(false);
        queueToBackground(new Runnable() {
          public void run() {
            changes.set(_update(nodeDescriptor));
          }
        }, new Runnable() {
          public void run() {
            result.setDone(changes.get());
          }
        }, nodeDescriptor);
      }
      else {
        result.setDone(_update(nodeDescriptor));
      }
    }
    else {
      if (edt || !myWasEverShown) {
        result.setDone(_update(nodeDescriptor));
      }
      else {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            execute(new Runnable() {
              public void run() {
                result.setDone(_update(nodeDescriptor));
              }
            });
          }
        });
      }
    }

    result.doWhenDone(new AsyncResult.Handler<Boolean>() {
      public void run(Boolean changes) {
        if (changes) {
          final long updateStamp = nodeDescriptor.getUpdateCount();
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
              Object element = nodeDescriptor.getElement();
              DefaultMutableTreeNode node = getNodeForElement(element, false);
              if (node != null) {
                TreePath path = getPathFor(node);
                if (path != null && myTree.isVisible(path)) {
                  updateNodeImageAndPosition(node, false);
                }
              }
            }
          });
        }
      }
    });


    return result;
  }

  private boolean _update(final NodeDescriptor nodeDescriptor) {
    try {
      final Ref<Boolean> update = new Ref<Boolean>();
      try {
        myStateLock.readLock().acquire();
        execute(new Runnable() {
          public void run() {
            nodeDescriptor.setUpdateCount(nodeDescriptor.getUpdateCount() + 1);
            update.set(getBuilder().updateNodeDescriptor(nodeDescriptor));
          }
        });
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException();
      } catch (ProcessCanceledException e) {
        throw e;        
      } finally {
        myStateLock.readLock().release();
      }
      return update.get();
    }
    catch (IndexNotReadyException e) {
      warnOnIndexNotReady();
      return false;
    }
  }

  private void assertIsDispatchThread() {
    if (isPassthroughMode()) return;

    if ((isTreeShowing() || myWasEverShown) && !isEdt()) {
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
    if (isPassthroughMode()) return;

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

  //todo: to make real callback
  public ActionCallback queueUpdate(Object element) {
    try {
      AbstractTreeUpdater updater = getUpdater();
      if (updater == null) {
        return new ActionCallback.Rejected();
      }

      final ActionCallback result = new ActionCallback();
      DefaultMutableTreeNode node = getNodeForElement(element, false);
      if (node != null) {
        addSubtreeToUpdate(node);
      }
      else {
        addSubtreeToUpdate(getRootNode());
      }

      updater.runAfterUpdate(new Runnable() {
        public void run() {
          result.setDone();
        }
      });
      return result;
    }
    catch (ProcessCanceledException e) {
      return new ActionCallback.Rejected();
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
    setHoldSize(true);

    boolean consumed = initRootNodeNowIfNeeded(pass);
    if (consumed) return;

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
    if (myUpdaterState != null && myUpdaterState.equals(state)) return state;

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

  protected void doUpdateNode(final DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    final NodeDescriptor descriptor = getDescriptorFrom(node);
    final Object prevElement = getElementFromDescriptor(descriptor);
    if (prevElement == null) return;
    update(descriptor, false).doWhenDone(new AsyncResult.Handler<Boolean>() {
      public void run(Boolean changes) {
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
    });
  }

  public Object getElementFromDescriptor(NodeDescriptor descriptor) {
    return getBuilder().getTreeStructureElement(descriptor);
  }

  private void updateNodeChildren(final DefaultMutableTreeNode node,
                                  final TreeUpdatePass pass,
                                  @Nullable final LoadedChildren loadedChildren,
                                  final boolean forcedNow,
                                  final boolean toSmartExpand,
                                  final boolean forceUpdate,
                                  final boolean descriptorIsUpToDate) {

    removeFromCancelled(node);

    execute(new Runnable() {
      public void run() {
        try {
          getTreeStructure().commit();


          final NodeDescriptor descriptor = getDescriptorFrom(node);
          if (descriptor == null) {
            removeLoading(node, true);
            return;
          }

          final boolean wasExpanded = myTree.isExpanded(new TreePath(node.getPath())) || isAutoExpand(node);
          final boolean wasLeaf = node.getChildCount() == 0;


          boolean bgBuild = isToBuildInBackground(descriptor);
          boolean notRequiredToUpdateChildren = !forcedNow && !wasExpanded;

          if (notRequiredToUpdateChildren && forceUpdate && !wasExpanded) {
            boolean alwaysPlus = getBuilder().isAlwaysShowPlus(descriptor);
            if (alwaysPlus && wasLeaf) {
              notRequiredToUpdateChildren = false;
            }
            else {
              notRequiredToUpdateChildren = alwaysPlus;
            }
          }

          final Ref<LoadedChildren> preloaded = new Ref<LoadedChildren>(loadedChildren);
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
                  update(descriptor, true);
                  descriptorWasUpdated = true;
                }

                if (processAlwaysLeaf(node)) return;

                Pair<Boolean, LoadedChildren> unbuilt = processUnbuilt(node, descriptor, pass, wasExpanded, null);

                if (unbuilt.getFirst()) return;
                preloaded.set(unbuilt.getSecond());
              }
            }
          }


          final boolean childForceUpdate = isChildNodeForceUpdate(node, forceUpdate, wasExpanded);

          if (!forcedNow && isToBuildInBackground(descriptor)) {
            if (processAlwaysLeaf(node)) return;

            queueBackgroundUpdate(
              new UpdateInfo(descriptor, pass, canSmartExpand(node, toSmartExpand), wasExpanded, childForceUpdate, descriptorWasUpdated), node);
            return;
          }
          else {
            if (!descriptorWasUpdated) {
              update(descriptor, false).doWhenDone(new Runnable() {
                public void run() {
                  if (processAlwaysLeaf(node)) return;
                  updateNodeChildrenNow(node, pass, preloaded.get(), toSmartExpand, wasExpanded, wasLeaf, childForceUpdate);
                }
              });
            }
            else {
              if (processAlwaysLeaf(node)) return;

              updateNodeChildrenNow(node, pass, preloaded.get(), toSmartExpand, wasExpanded, wasLeaf, childForceUpdate);
            }
          }
        }
        finally {
          if (isReleased()) return;

          processNodeActionsIfReady(node);
        }
      }
    }, node);
  }

  private boolean processAlwaysLeaf(DefaultMutableTreeNode node) {
    Object element = getElementFor(node);
    NodeDescriptor desc = getDescriptorFrom(node);

    if (desc == null) return false;

    if (getTreeStructure().isAlwaysLeaf(element)) {
      removeLoading(node, true);

      if (node.getChildCount() > 0) {
        final TreeNode[] children = new TreeNode[node.getChildCount()];
        for (int i = 0; i < node.getChildCount(); i++) {
          children[i] = node.getChildAt(i);
        }

        if (isSelectionInside(node)) {
          addSelectionPath(getPathFor(node), true, Condition.TRUE, null);
        }

        processInnerChange(new Runnable() {
          public void run() {
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

  private boolean isChildNodeForceUpdate(DefaultMutableTreeNode node, boolean parentForceUpdate, boolean parentExpanded) {
    TreePath path = getPathFor(node);
    return parentForceUpdate && (parentExpanded || myTree.isExpanded(path));
  }

  private void updateNodeChildrenNow(final DefaultMutableTreeNode node,
                                     final TreeUpdatePass pass,
                                     final LoadedChildren preloadedChildren,
                                     final boolean toSmartExpand,
                                     final boolean wasExpanded,
                                     final boolean wasLeaf,
                                     final boolean forceUpdate) {
    if (!canInitiateNewActivity()) {
      throw new ProcessCanceledException();
    }

    final NodeDescriptor descriptor = getDescriptorFrom(node);

    final MutualMap<Object, Integer> elementToIndexMap = loadElementsFromStructure(descriptor, preloadedChildren);
    final LoadedChildren loadedChildren =
      preloadedChildren != null ? preloadedChildren : new LoadedChildren(elementToIndexMap.getKeys().toArray());


    addToUpdating(node);
    pass.setCurrentNode(node);

    final boolean canSmartExpand = canSmartExpand(node, toSmartExpand);

    processExistingNodes(node, elementToIndexMap, pass, canSmartExpand(node, toSmartExpand), forceUpdate, wasExpanded, preloadedChildren)
      .doWhenDone(new Runnable() {
        public void run() {
          if (isDisposed(node)) {
            removeFromUpdating(node);
            return;
          }

          removeLoading(node, false);

          final boolean expanded = isExpanded(node, wasExpanded);

          if (expanded) {
            myWillBeExpaned.add(node);
          }
          else {
            myWillBeExpaned.remove(node);
          }

          collectNodesToInsert(descriptor, elementToIndexMap, node, expanded, loadedChildren)
            .doWhenDone(new AsyncResult.Handler<ArrayList<TreeNode>>() {
              public void run(ArrayList<TreeNode> nodesToInsert) {
                insertNodesInto(nodesToInsert, node);
                updateNodesToInsert(nodesToInsert, pass, canSmartExpand, isChildNodeForceUpdate(node, forceUpdate, expanded));
                removeLoading(node, true);
                removeFromUpdating(node);

                if (node.getChildCount() > 0) {
                  if (expanded) {
                    expand(node, canSmartExpand);
                  }
                }

                final Object element = getElementFor(node);
                addNodeAction(element, new NodeAction() {
                  public void onReady(final DefaultMutableTreeNode node) {
                    removeLoading(node, false);
                  }
                }, false);

                processNodeActionsIfReady(node);
              }
            }).doWhenProcessed(new Runnable() {
            public void run() {
              myWillBeExpaned.remove(node);
              removeFromUpdating(node);
              processNodeActionsIfReady(node);
            }
          });
        }
      }).doWhenRejected(new Runnable() {
      public void run() {
        removeFromUpdating(node);
        processNodeActionsIfReady(node);
      }
    });
  }

  private boolean isDisposed(DefaultMutableTreeNode node) {
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
    else if (myTree.isExpanded(path) || (isLeaf && parent != null && myTree.isExpanded(parent) && !myUnbuiltNodes.contains(last) && !isCancelled(last))) {
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
                                                       final boolean isExpanded,
                                                       final LoadedChildren loadedChildren) {
    final Ref<Pair<Boolean, LoadedChildren>> result = new Ref<Pair<Boolean, LoadedChildren>>();

    execute(new Runnable() {
      public void run() {
        if (!isExpanded && getBuilder().isAlwaysShowPlus(descriptor)) {
          result.set(new Pair<Boolean, LoadedChildren>(true, null));
          return;
        }

        final Object element = getElementFor(node);

        addToUpdating(node);

        try {
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

          removeFromUpdating(node);

          processNodeActionsIfReady(node);

          result.set(new Pair<Boolean, LoadedChildren>(processed, children));
        }
        finally {
          removeFromUpdating(node);
        }
      }
    });

    return result.get();
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
    final Ref<Object[]> passOne = new Ref<Object[]>();
    try {
      myStateLock.readLock().acquire();
      execute(new Runnable() {
        public void run() {
          passOne.set(getTreeStructure().getChildElements(element));
        }
      });
    }
    catch (IndexNotReadyException e) {
      warnOnIndexNotReady();
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    catch (InterruptedException e) {
      throw new ProcessCanceledException();
    } finally {
      myStateLock.readLock().release();
    }

    if (!Registry.is("ide.tree.checkStructure")) return passOne.get();

    final Object[] passTwo = getTreeStructure().getChildElements(element);

    final HashSet two = new HashSet(Arrays.asList(passTwo));

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

  private void warnOnIndexNotReady() {
    if (!myWasEverIndexNotReady) {
      myWasEverIndexNotReady = true;
      LOG.warn("Tree is not dumb-mode-aware; treeBuilder=" + getBuilder() + " treeStructure=" + getTreeStructure());
    }
  }

  private void updateNodesToInsert(final ArrayList<TreeNode> nodesToInsert,
                                   TreeUpdatePass pass,
                                   boolean canSmartExpand,
                                   boolean forceUpdate) {
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
              return processExistingNode(eachChild, getDescriptorFrom(eachChild), node, elementToIndexMap, pass, canSmartExpand,
                                         childForceUpdate, preloaded);
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
    if (pass.isExpired() || !canInitiateNewActivity()) return false;

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
        boolean wasRun = yieldAndRun(new Runnable() {
          public void run() {
            if (pass.isExpired()) {
              result.setRejected();
              return;
            }

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
              try {
                execute(processRunnable).notify(result);
              }
              catch (ProcessCanceledException e) {
                pass.expire();
                result.setRejected();
                cancelUpdate();
              }
            }
          }
        }, pass);
        if (!wasRun) {
          result.setRejected();
        }
      }
      else {
        try {
          execute(processRunnable).notify(result);
        }
        catch (ProcessCanceledException e) {
          pass.expire();
          result.setRejected();
          cancelUpdate();
        }
      }
    }

    return result;
  }

  private ActionCallback execute(final ActiveRunnable runnable) throws ProcessCanceledException {
    final ActionCallback result = new ActionCallback();
    execute(new Runnable() {
      public void run() {
        runnable.run().notify(result);
      }
    });
    return result;
  }

  private void execute(Runnable runnable)  {
    execute(runnable, null);
  }

  private void execute(Runnable runnable, @Nullable DefaultMutableTreeNode node) throws ProcessCanceledException {
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
      if (node != null) {
        addToCancelled(node);
      }
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

  private ActionCallback resetToReady() {
    final ActionCallback result = new ActionCallback();

    if (isReady()) {
      result.setDone();
      return result;
    }

    myResettingToReadyNow.set(true);

    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        Progressive[] progressives = myBatchIndicators.keySet().toArray(new Progressive[myBatchIndicators.size()]);
        for (Progressive each : progressives) {
          myBatchIndicators.remove(each).cancel();
          myBatchCallbacks.remove(each).setRejected();
        }

        resetToReadyNow().notify(result);
      }
    });

    return result;
  }

  private ActionCallback resetToReadyNow() {
    if (isReleased()) return new ActionCallback.Rejected();

    assertIsDispatchThread();

    DefaultMutableTreeNode[] uc = myUpdatingChildren.toArray(new DefaultMutableTreeNode[myUpdatingChildren.size()]);
    for (DefaultMutableTreeNode each : uc) {
      resetIncompleteNode(each);
    }


    Object[] bg = myLoadedInBackground.keySet().toArray(new Object[myLoadedInBackground.size()]);
    for (Object each : bg) {
      resetIncompleteNode(getNodeForElement(each, false));
    }

    myUpdaterState = null;
    getUpdater().reset();


    myYeildingNow = false;
    myYeildingPasses.clear();
    myYeildingDoneRunnables.clear();

    myNodeActions.clear();
    myNodeChildrenActions.clear();

    myUpdatingChildren.clear();
    myLoadedInBackground.clear();

    myDeferredExpansions.clear();
    myDeferredSelections.clear();

    ActionCallback result = getReady(this);
    result.doWhenDone(new Runnable() {
      public void run() {
        myResettingToReadyNow.set(false);
        setCancelRequested(false);
      }
    });

    maybeReady();

    return result;
  }

  public void addToCancelled(DefaultMutableTreeNode node) {
    myCancelledBuild.put(node, node);
  }

  public void removeFromCancelled(DefaultMutableTreeNode node) {
    myCancelledBuild.remove(node);
  }

  public boolean isCancelled(Object node) {
    if (node instanceof DefaultMutableTreeNode) {
      return myCancelledBuild.containsKey((DefaultMutableTreeNode)node);
    } else {
      return false;
    }
  }

  private void resetIncompleteNode(DefaultMutableTreeNode node) {
    addToCancelled(node);

    if (!isExpanded(node, false)) {
      node.removeAllChildren();
      if (!getTreeStructure().isAlwaysLeaf(getElementFor(node))) {
        insertLoadingNode(node, true);
      }
    }
    else {
      removeLoading(node, true);
    }
  }

  private boolean yieldAndRun(final Runnable runnable, final TreeUpdatePass pass) {
    myYeildingPasses.add(pass);
    myYeildingNow = true;
    yield(new Runnable() {
      public void run() {
        runOnYieldingDone(new Runnable() {
          public void run() {
            executeYieldingRequest(runnable, pass);
          }
        });
      }
    });

    return true;
  }

  public boolean isYeildingNow() {
    return myYeildingNow;
  }

  private boolean hasSheduledUpdates() {
    return getUpdater().hasNodesToUpdate();
  }

  public boolean isReady() {
    return isIdle() && !hasPendingWork() && !isNodeActionsPending();
  }

  public String getStatus() {
    return "isReady=" + isReady() + "\n" +
           " isIdle=" + isIdle() + "\n" +
           "  isYeildingNow=" + isYeildingNow() + "\n" +
           "  isWorkerBusy=" + isWorkerBusy() + "\n" +
           "  hasUpdatingNow=" + hasUpdatingNow() + "\n" +
           "  isLoadingInBackgroundNow=" + isLoadingInBackgroundNow() + "\n" +
           " hasPendingWork=" + hasPendingWork() + "\n" +
           "  hasNodesToUpdate=" + hasNodesToUpdate() + "\n" +
           "  updaterState=" + myUpdaterState + "\n" +
           "  hasScheduledUpdates=" + hasSheduledUpdates() + "\n" +
           "  isPostponedMode=" + getUpdater().isInPostponeMode() + "\n" +
           " nodeActions=" + myNodeActions.keySet() + "\n" +
           "isReleased=" + isReleased() + "\n" +
           " isReleaseRequested=" + isReleaseRequested() + "\n" +
           "isCancelProcessed=" + isCancelProcessed() + "\n" +
           " isCancelRequested=" + myCancelRequest + "\n" +
           " isResettingToReadyNow=" + myResettingToReadyNow + "\n" +
           "canInitiateNewActivity=" + canInitiateNewActivity();
  }

  public boolean hasPendingWork() {
    return hasNodesToUpdate() || (myUpdaterState != null && myUpdaterState.isProcessingNow()) || (hasSheduledUpdates() && !getUpdater().isInPostponeMode());
  }

  public boolean isIdle() {
    return !isYeildingNow() && !isWorkerBusy() && !hasUpdatingNow() && !isLoadingInBackgroundNow();
  }

  private void executeYieldingRequest(Runnable runnable, TreeUpdatePass pass) {
    try {
      try {
        myYeildingPasses.remove(pass);
        runnable.run();
      }
      finally {
        if (!isReleased()) {
          maybeYeildingFinished();
        }
      }
    }
    catch (ProcessCanceledException e) {
      resetToReady();
    }
  }

  private void maybeYeildingFinished() {
    if (myYeildingPasses.size() == 0) {
      myYeildingNow = false;
      flushPendingNodeActions();
    }
  }

  void maybeReady() {
    assertIsDispatchThread();

    if (isReleased()) return;

    if (isReady()) {
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

          if (!isReady()) {
            return;
          }
        }
      }

      setHoldSize(false);

      if (myTree.isShowing()) {
        if (getBuilder().isToEnsureSelectionOnFocusGained() && Registry.is("ide.tree.ensureSelectionOnFocusGained")) {
          TreeUtil.ensureSelection(myTree);
        }
      }

      if (myInitialized.isDone()) {
        myBusyObject.onReady();
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

  private MutualMap<Object, Integer> loadElementsFromStructure(final NodeDescriptor descriptor,
                                                               @Nullable LoadedChildren preloadedChildren) {
    MutualMap<Object, Integer> elementToIndexMap = new MutualMap<Object, Integer>(true);
    List children = preloadedChildren != null
                    ? preloadedChildren.getElements()
                    : Arrays.asList(getChildrenFor(getBuilder().getTreeStructureElement(descriptor)));
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

  private AsyncResult<ArrayList<TreeNode>> collectNodesToInsert(final NodeDescriptor descriptor,
                                                                final MutualMap<Object, Integer> elementToIndexMap,
                                                                final DefaultMutableTreeNode parent,
                                                                final boolean addLoadingNode,
                                                                @NotNull final LoadedChildren loadedChildren) {
    final AsyncResult<ArrayList<TreeNode>> result = new AsyncResult<ArrayList<TreeNode>>();

    final ArrayList<TreeNode> nodesToInsert = new ArrayList<TreeNode>();
    final Collection<Object> allElements = elementToIndexMap.getKeys();

    final ActionCallback processingDone = new ActionCallback(allElements.size());

    for (final Object child : allElements) {
      Integer index = elementToIndexMap.getValue(child);
      final Ref<NodeDescriptor> childDescr = new Ref<NodeDescriptor>(loadedChildren.getDescriptor(child));
      boolean needToUpdate = false;
      if (childDescr.get() == null) {
        childDescr.set(getTreeStructure().createDescriptor(child, descriptor));
        needToUpdate = true;
      }

      if (childDescr.get() == null) {
        processingDone.setDone();
        continue;
      }
      childDescr.get().setIndex(index.intValue());

      final ActionCallback update = new ActionCallback();
      if (needToUpdate) {
        update(childDescr.get(), false).doWhenDone(new AsyncResult.Handler<Boolean>() {
          public void run(Boolean changes) {
            loadedChildren.putDescriptor(child, childDescr.get(), changes);
            update.setDone();
          }
        });
      }
      else {
        update.setDone();
      }

      update.doWhenDone(new Runnable() {
        public void run() {
          Object element = getElementFromDescriptor(childDescr.get());
          if (element == null) {
            processingDone.setDone();
          }
          else {
            DefaultMutableTreeNode node = getNodeForElement(element, false);
            if (node == null || node.getParent() != parent) {
              final DefaultMutableTreeNode childNode = createChildNode(childDescr.get());
              if (addLoadingNode || getBuilder().isAlwaysShowPlus(childDescr.get())) {
                insertLoadingNode(childNode, true);
              }
              else {
                addToUnbuilt(childNode);
              }
              nodesToInsert.add(childNode);
              createMapping(element, childNode);
            }
            processingDone.setDone();
          }
        }
      });
    }

    processingDone.doWhenDone(new Runnable() {
      public void run() {
        result.setDone(nodesToInsert);
      }
    });

    return result;
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
    return myBusyObject.getReady(requestor);
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

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)getNodeForElement(element, false);
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
    return getUpdater().hasNodesToUpdate();
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

  public ActionCallback cancelUpdate() {
    if (isReleased()) return new ActionCallback.Rejected();

    setCancelRequested(true);

    final ActionCallback done = new ActionCallback();

    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (isReleased()) {
          done.setRejected();
          return;
        }

        if (myResettingToReadyNow.get()) {
          getReady(this).notify(done);
        } else if (isReady()) {
          resetToReadyNow();
          done.setDone();
        } else {
          if (isIdle() && hasPendingWork()) {
            resetToReadyNow();
            done.setDone();
          } else {
            getReady(this).notify(done);
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
    try {
      if (isUnitTestingMode()) {
        myStateLock.writeLock().acquire(); // in unit tests there should be solid sync, in production it's ok to have race conditions (to avoid blocking on acquire())
      } else {
        myStateLock.writeLock().attempt(Sync.ONE_SECOND);
      }
      myCancelRequest.set(requested);
    }
    catch (InterruptedException e) {
      return;
    }
    finally {
      myStateLock.writeLock().release();
    }
  }

  public ActionCallback batch(final Progressive progressive) {
    assertIsDispatchThread();

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    final ActionCallback callback = new ActionCallback();

    myBatchIndicators.put(progressive, indicator);
    myBatchCallbacks.put(progressive, callback);

    try {
      progressive.run(indicator);
    } catch (ProcessCanceledException e) {
      resetToReadyNow().doWhenProcessed(new Runnable() {
        public void run() {
          callback.setRejected();
        }
      });
      return callback;
    }finally {
      if (isReleased()) return new ActionCallback.Rejected();

      getReady(this).doWhenDone(new Runnable() {
        public void run() {
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

  public boolean isCancelProcessed() {
    try {
      myStateLock.readLock().acquire();
      return myCancelRequest.get() || myResettingToReadyNow.get();
    }
    catch (InterruptedException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } finally {
      myStateLock.readLock().release();
    }

    return false;
  }

  public boolean isToPaintSelection() {
    return isReady() || !mySelectionIsAdjusted;
  }

  public boolean isReleaseRequested() {
    return myReleaseRequested;
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
    return getUpdatingParent(kid) != null;
  }

  private DefaultMutableTreeNode getUpdatingParent(DefaultMutableTreeNode kid) {
    DefaultMutableTreeNode eachParent = kid;
    while (eachParent != null) {
      if (isUpdatingNow(eachParent)) return eachParent;
      eachParent = (DefaultMutableTreeNode)eachParent.getParent();
    }

    return null;
  }

  private boolean isLoadedInBackground(Object element) {
    return getLoadedInBackground(element) != null;
  }

  private UpdateInfo getLoadedInBackground(Object element) {
    synchronized (myLoadedInBackground) {
      return myLoadedInBackground.get(element);
    }
  }

  private void addToLoadedInBackground(Object element, UpdateInfo info) {
    synchronized (myLoadedInBackground) {
      myLoadedInBackground.put(element, info);
    }
  }

  private void removeFromLoadedInBackground(final Object element) {
    synchronized (myLoadedInBackground) {
      myLoadedInBackground.remove(element);
    }
  }

  private boolean isLoadingInBackgroundNow() {
    synchronized (myLoadedInBackground) {
      return myLoadedInBackground.size() > 0;
    }
  }

  private boolean queueBackgroundUpdate(final UpdateInfo updateInfo, final DefaultMutableTreeNode node) {
    assertIsDispatchThread();

    final Object oldElementFromDescriptor = getElementFromDescriptor(updateInfo.getDescriptor());

    UpdateInfo loaded = getLoadedInBackground(oldElementFromDescriptor);
    if (loaded != null) {
      loaded.apply(updateInfo);
      return false;
    }

    addToLoadedInBackground(oldElementFromDescriptor, updateInfo);

    if (!isNodeBeingBuilt(node)) {
      LoadingNode loadingNode = new LoadingNode(getLoadingNodeText());
      myTreeModel.insertNodeInto(loadingNode, node, node.getChildCount());
    }

    final Ref<LoadedChildren> children = new Ref<LoadedChildren>();
    final Ref<Object> elementFromDescriptor = new Ref<Object>();

    final DefaultMutableTreeNode[] nodeToProcessActions = new DefaultMutableTreeNode[1];

    final Runnable finalizeRunnable = new Runnable() {
      public void run() {
        invokeLaterIfNeeded(new Runnable() {
          public void run() {
            removeLoading(node, true);
            removeFromLoadedInBackground(elementFromDescriptor.get());
            removeFromLoadedInBackground(oldElementFromDescriptor);

            if (nodeToProcessActions[0] != null) {
              processNodeActionsIfReady(nodeToProcessActions[0]);
            }
          }
        });
      }
    };


    Runnable buildRunnable = new Runnable() {
      public void run() {
        if (updateInfo.getPass().isExpired()) {
          finalizeRunnable.run();
          return;
        }

        if (!updateInfo.isDescriptorIsUpToDate()) {
          update(updateInfo.getDescriptor(), true);
        }

        Object element = getElementFromDescriptor(updateInfo.getDescriptor());
        if (element == null) {
          removeFromLoadedInBackground(oldElementFromDescriptor);
          finalizeRunnable.run();
          return;
        }

        elementFromDescriptor.set(element);

        Object[] loadedElements = getChildrenFor(getBuilder().getTreeStructureElement(updateInfo.getDescriptor()));

        final LoadedChildren loaded = new LoadedChildren(loadedElements);
        for (final Object each : loadedElements) {
          final NodeDescriptor eachChildDescriptor = getTreeStructure().createDescriptor(each, updateInfo.getDescriptor());
          execute(new Runnable() {
            public void run() {
              loaded.putDescriptor(each, eachChildDescriptor, update(eachChildDescriptor, true).getResult());
            }
          });
        }

        children.set(loaded);

      }

      @Override
      public String toString() {
        return "runnable=" + oldElementFromDescriptor;
      }
    };

    Runnable updateRunnable = new Runnable() {
      public void run() {
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
          getUpdater().addSubtreeToUpdate(updateInfo.getPass());
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

        updateNodeChildren(node, updateInfo.getPass(), children.get(), true, updateInfo.isCanSmartExpand(), updateInfo.isForceUpdate(),
                           true);


        if (isRerunNeeded(updateInfo.getPass())) {
          getUpdater().addSubtreeToUpdate(updateInfo.getPass());
          return;
        }

        Object element = elementFromDescriptor.get();

        if (element != null) {
          removeLoading(node, true);
          nodeToProcessActions[0] = node;
        }
      }
    };
    queueToBackground(buildRunnable, updateRunnable, node).doWhenProcessed(finalizeRunnable).doWhenRejected(new Runnable() {
      public void run() {
        updateInfo.getPass().expire();
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
    assertIsDispatchThread();

    if (isNodeBeingBuilt(node)) return;

    final Object o = node.getUserObject();
    if (!(o instanceof NodeDescriptor)) return;


    if (isYeildingNow()) {
      myPendingNodeActions.add(node);
      return;
    }

    final Object element = getBuilder().getTreeStructureElement((NodeDescriptor)o);

    boolean childrenReady = !isLoadedInBackground(element);

    processActions(node, element, myNodeActions, childrenReady ? myNodeChildrenActions : null);
    if (childrenReady) {
      processActions(node, element, myNodeChildrenActions, null);
    }

    if (!isUpdatingParent(node) && !isWorkerBusy()) {
      final UpdaterTreeState state = myUpdaterState;
      if (myNodeActions.size() == 0 && state != null && !state.isProcessingNow()) {
        if (canInitiateNewActivity()) {
          if (!state.restore(childrenReady ? node : null)) {
            setUpdaterState(state);
          }
        }
      }
    }

    maybeReady();
  }


  private void processActions(DefaultMutableTreeNode node,
                              Object element,
                              final Map<Object, List<NodeAction>> nodeActions,
                              @Nullable final Map<Object, List<NodeAction>> secondaryNodeAction) {
    final List<NodeAction> actions = nodeActions.get(element);
    if (actions != null) {
      nodeActions.remove(element);

      List<NodeAction> secondary = secondaryNodeAction != null ? secondaryNodeAction.get(element) : null;
      for (NodeAction each : actions) {
        if (secondary != null && secondary.contains(each)) {
          secondary.remove(each);
        }
        each.onReady(node);
      }
    }
  }


  private boolean canSmartExpand(DefaultMutableTreeNode node, boolean canSmartExpand) {
    if (!getBuilder().isSmartExpand()) return false;

    boolean smartExpand = !myNotForSmartExpand.contains(node) && canSmartExpand;
    return smartExpand ? validateAutoExpand(smartExpand, getElementFor(node)) : false;
  }

  private void processSmartExpand(final DefaultMutableTreeNode node, final boolean canSmartExpand, boolean forced) {
    if (!getBuilder().isSmartExpand()) return;

    boolean can = canSmartExpand(node, canSmartExpand);

    if (!can && !forced) return;

    if (isNodeBeingBuilt(node) && !forced) {
      addNodeAction(getElementFor(node), new NodeAction() {
        public void onReady(DefaultMutableTreeNode node) {
          processSmartExpand(node, canSmartExpand, true);
        }
      }, true);
    }
    else {
      TreeNode child = getChildForSmartExpand(node);
      if (child != null) {
        final TreePath childPath = new TreePath(node.getPath()).pathByAddingChild(child);
        processInnerChange(new Runnable() {
          public void run() {
            myTree.expandPath(childPath);
          }
        });
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
    return getParentLoading(nodeObject) != null;
  }

  private DefaultMutableTreeNode getParentLoading(Object nodeObject) {
    if (!(nodeObject instanceof DefaultMutableTreeNode)) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodeObject;

    TreeNode eachParent = node.getParent();

    while (eachParent != null) {
      eachParent = eachParent.getParent();
      if (eachParent instanceof DefaultMutableTreeNode) {
        final Object eachElement = getElementFor((DefaultMutableTreeNode)eachParent);
        if (isLoadedInBackground(eachElement)) return (DefaultMutableTreeNode)eachParent;
      }
    }

    return null;
  }

  protected String getLoadingNodeText() {
    return IdeBundle.message("progress.searching");
  }

  private ActionCallback processExistingNode(final DefaultMutableTreeNode childNode,
                                             final NodeDescriptor childDescriptor,
                                             final DefaultMutableTreeNode parentNode,
                                             final MutualMap<Object, Integer> elementToIndexMap,
                                             final TreeUpdatePass pass,
                                             final boolean canSmartExpand,
                                             final boolean forceUpdate,
                                             LoadedChildren parentPreloadedChildren) {

    final ActionCallback result = new ActionCallback();

    if (pass.isExpired()) {
      return new ActionCallback.Rejected();
    }

    final Ref<NodeDescriptor> childDesc = new Ref<NodeDescriptor>(childDescriptor);

    if (childDesc.get() == null) {
      pass.expire();
      return new ActionCallback.Rejected();
    }
    final Object oldElement = getElementFromDescriptor(childDesc.get());
    if (oldElement == null) {
      pass.expire();
      return new ActionCallback.Rejected();
    }

    AsyncResult<Boolean> update = new AsyncResult<Boolean>();
    if (parentPreloadedChildren != null && parentPreloadedChildren.getDescriptor(oldElement) != null) {
      update.setDone(parentPreloadedChildren.isUpdated(oldElement));
    }
    else {
      update = update(childDesc.get(), false);
    }

    update.doWhenDone(new AsyncResult.Handler<Boolean>() {
      public void run(Boolean isChanged) {
        final Ref<Boolean> changes = new Ref<Boolean>(isChanged);

        final Ref<Boolean> forceRemapping = new Ref<Boolean>(false);
        final Ref<Object> newElement = new Ref<Object>(getElementFromDescriptor(childDesc.get()));

        final Integer index =
          newElement.get() != null ? elementToIndexMap.getValue(getBuilder().getTreeStructureElement(childDesc.get())) : null;
        final AsyncResult<Boolean> updateIndexDone = new AsyncResult<Boolean>();
        final ActionCallback indexReady = new ActionCallback();
        if (index != null) {
          final Object elementFromMap = elementToIndexMap.getKey(index);
          if (elementFromMap != newElement.get() && elementFromMap.equals(newElement.get())) {
            if (isInStructure(elementFromMap) && isInStructure(newElement.get())) {
              if (parentNode.getUserObject() instanceof NodeDescriptor) {
                final NodeDescriptor parentDescriptor = getDescriptorFrom(parentNode);
                childDesc.set(getTreeStructure().createDescriptor(elementFromMap, parentDescriptor));
                childNode.setUserObject(childDesc.get());
                newElement.set(elementFromMap);
                forceRemapping.set(true);
                update(childDesc.get(), false).doWhenDone(new AsyncResult.Handler<Boolean>() {
                  public void run(Boolean isChanged) {
                    changes.set(isChanged);
                    updateIndexDone.setDone(isChanged);
                  }
                });
              }
            }
            else {
              updateIndexDone.setDone(changes.get());
            }
          }
          else {
            updateIndexDone.setDone(changes.get());
          }

          updateIndexDone.doWhenDone(new Runnable() {
            public void run() {
              if (childDesc.get().getIndex() != index.intValue()) {
                changes.set(true);
              }
              childDesc.get().setIndex(index.intValue());
              indexReady.setDone();
            }
          });
        }
        else {
          updateIndexDone.setDone();
        }

        updateIndexDone.doWhenDone(new Runnable() {
          public void run() {
            if (index != null && changes.get()) {
              updateNodeImageAndPosition(childNode, false);
            }
            if (!oldElement.equals(newElement.get()) | forceRemapping.get()) {
              removeMapping(oldElement, childNode, newElement.get());
              if (newElement.get() != null) {
                createMapping(newElement.get(), childNode);
              }
              NodeDescriptor parentDescriptor = getDescriptorFrom(parentNode);
              if (parentDescriptor != null) {
                parentDescriptor.setChildrenSortingStamp(-1);
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
              elementToIndexMap.remove(getBuilder().getTreeStructureElement(childDesc.get()));
              updateNodeChildren(childNode, pass, null, false, canSmartExpand, forceUpdate, true);
            }

            if (parentNode.equals(getRootNode())) {
              myTreeModel.nodeChanged(getRootNode());
            }

            result.setDone();
          }
        });
      }
    });


    return result;
  }

  private void adjustSelectionOnChildRemove(DefaultMutableTreeNode parentNode, int selectedIndex, Object disposedElement) {
    DefaultMutableTreeNode node = getNodeForElement(disposedElement, false);
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

  private void addSelectionPath(final TreePath path,
                                final boolean isAdjustedSelection,
                                final Condition isExpiredAdjustement,
                                @Nullable final Object adjustmentCause) {
    processInnerChange(new Runnable() {
      public void run() {
        TreePath toSelect = null;

        if (isLoadingNode(path.getLastPathComponent())) {
          final TreePath parentPath = path.getParentPath();
          if (parentPath != null) {
            if (isValidForSelectionAdjusting((TreeNode)parentPath.getLastPathComponent())) {
              toSelect = parentPath;
            }
            else {
              toSelect = null;
            }
          }
        }
        else {
          toSelect = path;
        }

        if (toSelect != null) {
          mySelectionIsAdjusted = isAdjustedSelection;

          myTree.addSelectionPath(toSelect);

          if (isAdjustedSelection && myUpdaterState != null) {
            final Object toSelectElement = getElementFor(toSelect.getLastPathComponent());
            myUpdaterState.addAdjustedSelection(toSelectElement, isExpiredAdjustement, adjustmentCause);
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
    processInnerChange(new Runnable() {
      public void run() {
        if (willAdjustSelection) {
          final TreePath path = getPathFor(node);
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

  private void expandPath(final TreePath path, final boolean canSmartExpand) {
    processInnerChange(new Runnable() {
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
    return myUpdaterState != null && myUpdaterState.isProcessingNow();
  }

  protected boolean doUpdateNodeDescriptor(final NodeDescriptor descriptor) {
    return descriptor.update();
  }

  private void makeLoadingOrLeafIfNoChildren(final DefaultMutableTreeNode node) {
    TreePath path = getPathFor(node);
    if (path == null) return;

    insertLoadingNode(node, true);

    final NodeDescriptor descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;

    descriptor.setChildrenSortingStamp(-1);

    if (getBuilder().isAlwaysShowPlus(descriptor)) return;


    TreePath parentPath = path.getParentPath();
    if (myTree.isVisible(path) || (parentPath != null && myTree.isExpanded(parentPath))) {
      if (myTree.isExpanded(path)) {
        addSubtreeToUpdate(node);
      }
      else {
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


  protected ActionCallback queueToBackground(@NotNull final Runnable bgBuildAction, @Nullable final Runnable edtPostRunnable, final Object id) {
    if (!canInitiateNewActivity()) return new ActionCallback.Rejected();

    final ActionCallback result = new ActionCallback();

    final Ref<Boolean> fail = new Ref<Boolean>(false);
    final Runnable finalizer = new Runnable() {
      public void run() {
        if (fail.get()) {
          result.setRejected();
        }
        else {
          result.setDone();
        }
      }
    };

    registerWorkerTask(bgBuildAction, id);

    final Runnable pooledThreadWithProgressRunnable = new Runnable() {
      public void run() {
        try {
          final AbstractTreeBuilder builder = getBuilder();

          if (!canInitiateNewActivity()) {
            throw new ProcessCanceledException();
          }

          builder.runBackgroundLoading(new Runnable() {
            public void run() {

              assertNotDispatchThread();
              try {
                if (!canInitiateNewActivity()) {
                  throw new ProcessCanceledException();
                }

                execute(bgBuildAction);

                if (edtPostRunnable != null) {

                  builder.updateAfterLoadedInBackground(new Runnable() {
                    public void run() {
                      try {
                        assertIsDispatchThread();
                        if (!canInitiateNewActivity()) {
                          throw new ProcessCanceledException();
                        }

                        execute(edtPostRunnable);

                      }
                      catch (ProcessCanceledException e) {
                        fail.set(true);
                        cancelUpdate();
                      }
                      finally {
                        unregisterWorkerTask(bgBuildAction, finalizer, id);
                      }
                    }
                  });
                }
                else {
                  unregisterWorkerTask(bgBuildAction, finalizer, id);
                }
              }
              catch (ProcessCanceledException e) {
                fail.set(true);
                unregisterWorkerTask(bgBuildAction, finalizer, id);
                cancelUpdate();
              }
              catch (Throwable t) {
                unregisterWorkerTask(bgBuildAction, finalizer, id);
                throw new RuntimeException(t);
              }
            }
          });
        }
        catch (ProcessCanceledException e) {
          unregisterWorkerTask(bgBuildAction, finalizer, id);
          cancelUpdate();
        }
      }
    };

    Runnable pooledThreadRunnable = new Runnable() {
      public void run() {
        try {
          if (myProgress != null) {
            ProgressManager.getInstance().runProcess(pooledThreadWithProgressRunnable, myProgress);
          }
          else {
            execute(pooledThreadWithProgressRunnable);
          }
        }
        catch (ProcessCanceledException e) {
          fail.set(true);
          unregisterWorkerTask(bgBuildAction, finalizer, id);
          cancelUpdate();
        }
      }
    };

    if (isPassthroughMode()) {
      execute(pooledThreadRunnable);
    }
    else {
      if (myWorker == null || myWorker.isDisposed()) {
        myWorker = new WorkerThread("AbstractTreeBuilder.Worker", 1);
        myWorker.start();
        myWorker.addTaskFirst(pooledThreadRunnable);
        myWorker.dispose(false);
      }
      else {
        myWorker.addTaskFirst(pooledThreadRunnable);
      }
    }

    return result;
  }

  private void registerWorkerTask(Runnable runnable, Object id) {
    synchronized (myActiveWorkerTasks) {
      myActiveWorkerTasks.add(runnable);
    }
  }

  private void unregisterWorkerTask(Runnable runnable, @Nullable Runnable finalizeRunnable, Object id) {
    boolean wasRemoved;

    synchronized (myActiveWorkerTasks) {
      wasRemoved = myActiveWorkerTasks.remove(runnable);
    }

    if (wasRemoved && finalizeRunnable != null) {
      finalizeRunnable.run();
    }

    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        maybeReady();
      }
    });
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
          sortChildren(node, children, true, false);
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

  private void insertNodesInto(final ArrayList<TreeNode> toInsert, final DefaultMutableTreeNode parentNode) {
    sortChildren(parentNode, toInsert, false, true);
    final ArrayList<TreeNode> all = new ArrayList<TreeNode>(toInsert.size() + parentNode.getChildCount());
    all.addAll(toInsert);
    all.addAll(TreeUtil.childrenToArray(parentNode));

    if (toInsert.size() > 0) {
      sortChildren(parentNode, all, true, true);

      int[] newNodeIndices = new int[toInsert.size()];
      int eachNewNodeIndex = 0;
      TreeMap<Integer, TreeNode> insertSet = new TreeMap<Integer, TreeNode>();
      for (int i = 0; i < toInsert.size(); i++) {
        TreeNode eachNewNode = toInsert.get(i);
        while (all.get(eachNewNodeIndex) != eachNewNode) {
          eachNewNodeIndex++;
        }
        newNodeIndices[i] = eachNewNodeIndex;
        insertSet.put(eachNewNodeIndex, eachNewNode);
      }

      Iterator<Integer> indices = insertSet.keySet().iterator();
      while (indices.hasNext()) {
        Integer eachIndex = indices.next();
        TreeNode eachNode = insertSet.get(eachIndex);
        parentNode.insert((MutableTreeNode)eachNode, eachIndex);
      }

      myTreeModel.nodesWereInserted(parentNode, newNodeIndices);
    }
    else {
      ArrayList<TreeNode> before = new ArrayList<TreeNode>();
      before.addAll(all);

      sortChildren(parentNode, all, true, false);
      if (!before.equals(all)) {
        processInnerChange(new Runnable() {
          public void run() {
            Enumeration<TreePath> expanded = getTree().getExpandedDescendants(getPathFor(parentNode));
            TreePath[] selected = getTree().getSelectionModel().getSelectionPaths();

            parentNode.removeAllChildren();
            for (TreeNode each : all) {
              parentNode.add((MutableTreeNode)each);
            }
            myTreeModel.nodeStructureChanged(parentNode);

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

  private void sortChildren(DefaultMutableTreeNode node, ArrayList<TreeNode> children, boolean updateStamp, boolean forceSort) {
    NodeDescriptor descriptor = getDescriptorFrom(node);
    assert descriptor != null;

    if (descriptor.getChildrenSortingStamp() >= getComparatorStamp() && !forceSort) return;
    if (children.size() > 0) {
      getBuilder().sortChildren(myNodeComparator, node, children);
    }

    if (updateStamp) {
      descriptor.setChildrenSortingStamp(getComparatorStamp());
    }
  }

  private void disposeNode(DefaultMutableTreeNode node) {
    TreeNode parent = node.getParent();
    if (parent instanceof DefaultMutableTreeNode) {
      addToUnbuilt((DefaultMutableTreeNode)parent);
    }

    if (node.getChildCount() > 0) {
      for (DefaultMutableTreeNode _node = (DefaultMutableTreeNode)node.getFirstChild(); _node != null; _node = _node.getNextSibling()) {
        disposeNode(_node);
      }
    }

    removeFromUpdating(node);
    removeFromUnbuilt(node);
    removeFromCancelled(node);

    if (isLoadingNode(node)) return;
    NodeDescriptor descriptor = getDescriptorFrom(node);
    if (descriptor == null) return;
    final Object element = getElementFromDescriptor(descriptor);
    removeMapping(element, node, null);
    myAutoExpandRoots.remove(element);
    node.setUserObject(null);
    node.removeAllChildren();
  }

  public boolean addSubtreeToUpdate(final DefaultMutableTreeNode root) {
    return addSubtreeToUpdate(root, null);
  }

  public boolean addSubtreeToUpdate(final DefaultMutableTreeNode root, final Runnable runAfterUpdate) {
    Object element = getElementFor(root);
    if (getTreeStructure().isAlwaysLeaf(element)) {
      removeLoading(root, true);

      if (runAfterUpdate != null) {
        getReady(this).doWhenDone(runAfterUpdate);
      }
      return false;
    }

    execute(new Runnable() {
      public void run() {
        getUpdater().runAfterUpdate(runAfterUpdate);
        getUpdater().addSubtreeToUpdate(root);
      }
    });

    return true;
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
    _select(elements, onDone, addToSelection, true, false, true, deferred, false, false);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure) {

    _select(elements, onDone, addToSelection, checkCurrentSelection, checkIfInStructure, true, false, false, false);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure,
               final boolean scrollToVisible) {

    _select(elements, onDone, addToSelection, checkCurrentSelection, checkIfInStructure, scrollToVisible, false, false, false);
  }

  public void userSelect(final Object[] elements, final Runnable onDone, final boolean addToSelection, boolean scroll) {
    _select(elements, onDone, addToSelection, true, false, scroll, false, true, true);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure,
               final boolean scrollToVisible,
               final boolean deferred,
               final boolean canSmartExpand,
               final boolean mayQueue) {

    AbstractTreeUpdater updater = getUpdater();
    if (mayQueue && updater != null) {
      updater.queueSelection(
        new SelectionRequest(elements, onDone, addToSelection, checkCurrentSelection, checkIfInStructure, scrollToVisible, deferred,
                             canSmartExpand));
      return;
    }

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
                processInnerChange(new Runnable() {
                  public void run() {
                    restoreSelection(currentElements);
                  }
                });
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
        addSelectionPath(getPathFor(node), false, null, null);
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
                        final boolean canSmartExpand) {
    final Runnable _onDone = new Runnable() {
      public void run() {
        if (!checkDeferred(deferred, onDone)) return;

        checkPathAndMaybeRevalidate(element, new Runnable() {
          public void run() {
            selectVisible(element, onDone, addToSelection, canBeCentered, scrollToVisible);
          }
        }, true, false, canSmartExpand);
      }
    };
    _expand(element, _onDone, true, false, canSmartExpand);
  }

  private void checkPathAndMaybeRevalidate(Object element, final Runnable onDone, final boolean parentsOnly, final boolean checkIfInStructure, final boolean canSmartExpand) {
    boolean toRevalidate = !myRevalidatedObjects.contains(element) && getNodeForElement(element, false) == null && isInStructure(element);
    if (!toRevalidate) {
      runDone(onDone);
      return;
    }

    myRevalidatedObjects.add(element);
    AsyncResult<Object> revalidated = getBuilder().revalidateElement(element);
    if (revalidated == null) {
      runDone(onDone);
      return;
    }

    revalidated.doWhenDone(new AsyncResult.Handler<Object>() {
      public void run(final Object o) {
        invokeLaterIfNeeded(new Runnable() {
          public void run() {
            _expand(o, onDone, parentsOnly, checkIfInStructure, canSmartExpand);
          }
        });
      }
    }).doWhenRejected(new Runnable() {
      public void run() {
        runDone(onDone);
      }
    });
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
      setHoldSize(false);
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
      setHoldSize(false);
      TreeUtil.showAndSelect(myTree, row - 2, row + 2, row, -1, addToSelection, scroll).doWhenDone(new Runnable() {
        public void run() {
          runDone(onDone);
        }
      });
    }
  }

  public void expandAll(@Nullable final Runnable onDone) {
    final JTree tree = getTree();
    if (tree.getRowCount() > 0) {
      final int expandRecursionDepth = Math.max(2, Registry.intValue("ide.tree.expandRecursionDepth"));
      new Runnable() {
        private int myCurrentRow = 0;
        private int myInvocationCount = 0;

        public void run() {
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
            final int row = myCurrentRow++;
            if (row < tree.getRowCount()) {
              final TreePath path = tree.getPathForRow(row);
              final Object last = path.getLastPathComponent();
              final Object elem = getElementFor(last);
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

  public void expand(final Object element, @Nullable final Runnable onDone) {
    expand(new Object[]{element}, onDone);
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

    try {
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
          }).doWhenRejected(new Runnable() {
            public void run() {
              runDone(onDone);
            }
          });

          expandNext(element, 0, parentsOnly, checkIfInStructure, canSmartExpand, done, 0);
        }
      });
    }
    catch (ProcessCanceledException e) {
      runDone(onDone);
    }
  }

  private void expandNext(final Object[] elements,
                          final int index,
                          final boolean parentsOnly,
                          final boolean checkIfInStricture,
                          final boolean canSmartExpand,
                          final ActionCallback done,
                          final int currentDepth) {
    if (elements.length <= 0) {
      done.setDone();
      return;
    }

    if (index >= elements.length) {
      return;
    }

    final int[] actualDepth = new int[]{currentDepth};
    boolean breakCallChain = false;
    if (actualDepth[0] > Registry.intValue("ide.tree.expandRecursionDepth")) {
      actualDepth[0] = 0;
      breakCallChain = true;
    }

    Runnable expandRunnable = new Runnable() {
      public void run() {
        _expand(elements[index], new Runnable() {
          public void run() {
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
    if (done == null) return;

    if (isYeildingNow()) {
      if (!myYeildingDoneRunnables.contains(done)) {
        myYeildingDoneRunnables.add(done);
      }
    }
    else {
      execute(done);
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
        eachElement = eachElement != null ? getTreeStructure().getParentElement(eachElement) : null;
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

        checkPathAndMaybeRevalidate(kidsToExpand.get(expandIndex - 1), new Runnable() {
          public void run() {
            final DefaultMutableTreeNode nextNode = getNodeForElement(kidsToExpand.get(expandIndex - 1), false);
            processExpand(nextNode, kidsToExpand, expandIndex - 1, onDone, canSmartExpand);
          }
        }, false, false, canSmartExpand);
      }
    }, true);


    boolean childrenToUpdate = areChildrenToBeUpdated(toExpand);
    boolean expanded = myTree.isExpanded(getPathFor(toExpand));
    boolean unbuilt = myUnbuiltNodes.contains(toExpand);

    if (expanded) {
      if (unbuilt && !childrenToUpdate) {
        addSubtreeToUpdate(toExpand);
      } else if (childrenToUpdate) {
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
    return getParentBuiltNode(node) != null;
  }

  public final DefaultMutableTreeNode getParentBuiltNode(Object node) {
    DefaultMutableTreeNode parent = getParentLoading(node);
    if (parent != null) return parent;

    if (isLoadingParent(node)) return (DefaultMutableTreeNode)node;

    final boolean childrenAreNoLoadedYet = myUnbuiltNodes.contains(node);
    if (childrenAreNoLoadedYet) {
      if (node instanceof DefaultMutableTreeNode) {
        final TreePath nodePath = new TreePath(((DefaultMutableTreeNode)node).getPath());
        if (!myTree.isExpanded(nodePath)) return null;
      }

      return (DefaultMutableTreeNode)node;
    }


    return null;
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

    if (myUpdater != null) {
      myUpdater.setPassThroughMode(myPassthroughMode);
    }
  }

  public DefaultMutableTreeNode getRootNode() {
    return myRootNode;
  }

  public void setRootNode(@NotNull final DefaultMutableTreeNode rootNode) {
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
    if (!canInitiateNewActivity()) return;

    final UpdaterTreeState state = new UpdaterTreeState(this);

    myTree.collapsePath(new TreePath(myTree.getModel().getRoot()));
    myTree.clearSelection();
    getRootNode().removeAllChildren();

    myRootNodeWasInitialized = false;
    clearNodeActions();
    myElementToNodeMap.clear();
    myDeferredSelections.clear();
    myDeferredExpansions.clear();
    myLoadedInBackground.clear();
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

  private class MySelectionListener implements TreeSelectionListener {
    public void valueChanged(final TreeSelectionEvent e) {
      if (mySilentSelect != null && mySilentSelect.equals(e.getNewLeadSelectionPath())) return;

      dropUpdaterStateIfExternalChange();
    }
  }


  private class MyExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      final TreePath path = event.getPath();

      if (mySilentExpand != null && mySilentExpand.equals(path)) return;

      dropUpdaterStateIfExternalChange();

      if (myRequestedExpand != null && !myRequestedExpand.equals(path)) {
        getReady(AbstractTreeUi.this).doWhenDone(new Runnable() {
          public void run() {
            Object element = getElementFor(path.getLastPathComponent());
            expand(element, null);
          }
        });
        return;
      }


      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();

      if (!myUnbuiltNodes.contains(node)) {
        removeLoading(node, false);

        Set<DefaultMutableTreeNode> childrenToUpdate = new HashSet<DefaultMutableTreeNode>();
        for (int i = 0; i < node.getChildCount(); i++) {
          DefaultMutableTreeNode each = (DefaultMutableTreeNode)node.getChildAt(i);
          if (myUnbuiltNodes.contains(each)) {
            makeLoadingOrLeafIfNoChildren(each);
            childrenToUpdate.add(each);
          }
        }

        if (childrenToUpdate.size() > 0) {
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
        addSelectionPath(pathToSelect, true, Condition.FALSE, null);
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
  }

  private void maybeUpdateSubtreeToUpdate(final DefaultMutableTreeNode subtreeRoot) {
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
      addNodeAction(getElementFor(subtreeRoot), new NodeAction() {
        public void onReady(DefaultMutableTreeNode parent) {
          maybeUpdateSubtreeToUpdate(subtreeRoot);
        }
      }, true);
    }
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

  class LoadedChildren {

    private final List myElements;
    private final Map<Object, NodeDescriptor> myDescriptors = new HashMap<Object, NodeDescriptor>();
    private final Map<NodeDescriptor, Boolean> myChanges = new HashMap<NodeDescriptor, Boolean>();

    LoadedChildren(Object[] elements) {
      myElements = Arrays.asList(elements != null ? elements : ArrayUtil.EMPTY_OBJECT_ARRAY);
    }

    void putDescriptor(Object element, NodeDescriptor descriptor, boolean isChanged) {
      if (isUnitTestingMode()) {
        assert myElements.contains(element);
      }
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

  private long getComparatorStamp() {
    if (myNodeDescriptorComparator instanceof NodeDescriptor.NodeComparator) {
      long currentComparatorStamp = ((NodeDescriptor.NodeComparator)myNodeDescriptorComparator).getStamp();
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

  public void incComparatorStamp() {
    myOwnComparatorStamp = getComparatorStamp() + 1;
  }

  public static class UpdateInfo {
    NodeDescriptor myDescriptor;
    TreeUpdatePass myPass;
    boolean myCanSmartExpand;
    boolean myWasExpanded;
    boolean myForceUpdate;
    boolean myDescriptorIsUpToDate;

    public UpdateInfo(NodeDescriptor descriptor,
                      TreeUpdatePass pass,
                      boolean canSmartExpand,
                      boolean wasExpanded,
                      boolean forceUpdate,
                      boolean descriptorIsUpToDate) {
      myDescriptor = descriptor;
      myPass = pass;
      myCanSmartExpand = canSmartExpand;
      myWasExpanded = wasExpanded;
      myForceUpdate = forceUpdate;
      myDescriptorIsUpToDate = descriptorIsUpToDate;
    }

    synchronized NodeDescriptor getDescriptor() {
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

    public synchronized void apply(UpdateInfo updateInfo) {
      myDescriptor = updateInfo.myDescriptor;
      myPass = updateInfo.myPass;
      myCanSmartExpand = updateInfo.myCanSmartExpand;
      myWasExpanded = updateInfo.myWasExpanded;
      myForceUpdate = updateInfo.myForceUpdate;
      myDescriptorIsUpToDate = updateInfo.myDescriptorIsUpToDate;
    }

    public String toString() {
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


  public void setPassthroughMode(boolean passthrough) {
    myPassthroughMode = passthrough;
    AbstractTreeUpdater updater = getUpdater();

    if (updater != null) {
      updater.setPassThroughMode(myPassthroughMode);
    }

    if (!isUnitTestingMode() && passthrough) {
      // TODO: this assertion should be restored back as soon as possible [JamTreeTableView should be rewritten, etc]
      //LOG.error("Pass-through mode for TreeUi is allowed only for unit test mode");
    }
  }

  public boolean isPassthroughMode() {
    return myPassthroughMode;
  }

  private boolean isUnitTestingMode() {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  private void addModelListenerToDianoseAccessOutsideEdt() {
    myTreeModel.addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
        assertIsDispatchThread();
      }

      public void treeNodesInserted(TreeModelEvent e) {
        assertIsDispatchThread();
      }

      public void treeNodesRemoved(TreeModelEvent e) {
        assertIsDispatchThread();
      }

      public void treeStructureChanged(TreeModelEvent e) {
        assertIsDispatchThread();
      }
    });
  }

}
