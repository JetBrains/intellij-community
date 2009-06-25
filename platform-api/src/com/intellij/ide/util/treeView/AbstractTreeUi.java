package com.intellij.ide.util.treeView;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Time;
import com.intellij.util.concurrency.WorkerThread;
import com.intellij.util.containers.HashSet;
import com.intellij.util.enumeration.EnumerationCopy;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class AbstractTreeUi {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeBuilder");
  protected JTree myTree;// protected for TestNG
  @SuppressWarnings({"WeakerAccess"}) protected DefaultTreeModel myTreeModel;
  private AbstractTreeStructure myTreeStructure;
  private AbstractTreeUpdater myUpdater;
  private Comparator<NodeDescriptor> myNodeDescriptorComparator;
  private final Comparator<TreeNode> myNodeComparator = new Comparator<TreeNode>() {
    public int compare(TreeNode n1, TreeNode n2) {
      if (isLoadingNode(n1) || isLoadingNode(n2)) return 0;
      NodeDescriptor nodeDescriptor1 = (NodeDescriptor)((DefaultMutableTreeNode)n1).getUserObject();
      NodeDescriptor nodeDescriptor2 = (NodeDescriptor)((DefaultMutableTreeNode)n2).getUserObject();
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
  private ProgressIndicator myProgress;
  private static final int WAIT_CURSOR_DELAY = 100;
  private AbstractTreeNode<Object> TREE_NODE_WRAPPER;
  private boolean myRootNodeWasInitialized = false;
  private final Map<Object, List<NodeAction>> myNodeActions = new HashMap<Object, List<NodeAction>>();
  private boolean myUpdateFromRootRequested;
  private boolean myWasEverShown;
  private boolean myUpdateIfInactive;
  private final List<Object> myLoadingParents = new ArrayList<Object>();
  private long myClearOnHideDelay = -1;
  private ScheduledExecutorService ourClearanceService;
  private final Map<AbstractTreeUi, Long> ourUi2Countdown = Collections.synchronizedMap(new WeakHashMap<AbstractTreeUi, Long>());
  private final List<Runnable> myDeferredSelections = new ArrayList<Runnable>();
  private final List<Runnable> myDeferredExpansions = new ArrayList<Runnable>();
  private UpdaterTreeState myUpdaterState;
  private AbstractTreeBuilder myBuilder;

  private final Set<DefaultMutableTreeNode> myUpdatingChildren = new HashSet<DefaultMutableTreeNode>();
  private long myJanitorPollPeriod = Time.SECOND * 10;
  private boolean myCheckStructure = false;


  private boolean myCanYield = false;

  private final List<TreeUpdatePass> myYeildingPasses = new ArrayList<TreeUpdatePass>();

  private boolean myYeildingNow;

  private List<DefaultMutableTreeNode> myPendingNodeActions = new ArrayList<DefaultMutableTreeNode>();
  private List<Runnable> myYeildingDoneRunnables = new ArrayList<Runnable>();

  private Alarm myBusyAlarm = new Alarm();
  private Runnable myWaiterForReady = new Runnable() {
    public void run() {
      maybeSetBusyAndScheduleWaiterForReady(false);
    }
  };

  private RegistryValue myYeildingUpdate = Registry.get("ide.tree.yeildingUiUpdate");
  private RegistryValue myShowBusyIndicator = Registry.get("ide.tree.showBusyIndicator");
  private RegistryValue myWaitForReadyTime = Registry.get("ide.tree.waitForReadyTimout");

  protected final void init(AbstractTreeBuilder builder,
                            JTree tree,
                            DefaultTreeModel treeModel,
                            AbstractTreeStructure treeStructure,
                            Comparator<NodeDescriptor> comparator) {

    init(builder, tree, treeModel, treeStructure, comparator, true);
  }

  protected void init(AbstractTreeBuilder builder,
                      JTree tree,
                      DefaultTreeModel treeModel,
                      AbstractTreeStructure treeStructure,
                      Comparator<NodeDescriptor> comparator,
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
        if (!isReleased()) {
          AbstractTreeUi.this.showNotify();
        }
      }

      public void hideNotify() {
        if (!isReleased()) {
          AbstractTreeUi.this.hideNotify();
        }
      }
    });
    Disposer.register(getBuilder(), uiNotify);
  }

  protected void hideNotify() {
    myBusyAlarm.cancelAllRequests();

    if (!myWasEverShown) return;

    if (!myNodeActions.isEmpty()) {
      cancelBackgroundLoading();
      myUpdateFromRootRequested = true;
    }

    if (myClearOnHideDelay >= 0) {
      ourUi2Countdown.put(this, System.currentTimeMillis() + myClearOnHideDelay);
      initClearanceServiceIfNeeded();
    }
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
      } else {
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
    if (ourClearanceService != null) {
      ourClearanceService.shutdown();
      ourClearanceService = null;
    }
  }

  void showNotify() {
    ourUi2Countdown.remove(this);

    if (!myWasEverShown || myUpdateFromRootRequested) {
      if (wasRootNodeInitialized()) {
        getBuilder().updateFromRoot();
      }
      else {
        initRootNodeNowIfNeeded(new TreeUpdatePass(getRootNode()));
        getBuilder().updateFromRoot();
      }
    }
    myWasEverShown = true;
  }

  public void release() {
    if (isReleased()) return;

    myTree.removeTreeExpansionListener(myExpansionListener);
    myTree.removeTreeSelectionListener(mySelectionListener);
    disposeNode(getRootNode());
    myElementToNodeMap.clear();
    getUpdater().cancelAllRequests();
    if (myWorker != null) {
      myWorker.dispose(true);
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
  }

  public boolean isReleased() {
    return myBuilder == null;
  }

  protected void doExpandNodeChildren(final DefaultMutableTreeNode node) {
    getTreeStructure().commit();
    addNodeAction(getElementFor(node), new NodeAction() {
      public void onReady(final DefaultMutableTreeNode node) {
        processSmartExpand(node);
      }
    });
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
          expand(node);
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
        expand(node);
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
    Collections.sort(childNodes, myNodeComparator);
    for (TreeNode childNode1 : childNodes) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
      node.add(childNode);
      resortChildren(childNode);
    }
  }

  protected final void initRootNode() {
    final Activatable activatable = new Activatable() {
      public void showNotify() {
        if (!myRootNodeWasInitialized) {
          initRootNodeNowIfNeeded(new TreeUpdatePass(getRootNode()));
        }
      }

      public void hideNotify() {
      }
    };

    if (myUpdateIfInactive || ApplicationManager.getApplication().isUnitTestMode()) {
      activatable.showNotify();
    }
    else {
      new UiNotifyConnector.Once(myTree, activatable);
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
    });
    NodeDescriptor nodeDescriptor = getTreeStructure().createDescriptor(rootElement, null);
    getRootNode().setUserObject(nodeDescriptor);
    update(nodeDescriptor, false);
    if (getElementFromDescriptor(nodeDescriptor) != null) {
      createMapping(getElementFromDescriptor(nodeDescriptor), getRootNode());
    }
    addLoadingNode(getRootNode());
    boolean willUpdate = false;
    if (getBuilder().isAutoExpandNode(nodeDescriptor)) {
      willUpdate = myUnbuiltNodes.contains(getRootNode());
      expand(getRootNode());
    }
    if (!willUpdate) {
      updateNodeChildren(getRootNode(), pass);
    }
    if (getRootNode().getChildCount() == 0) {
      myTreeModel.nodeChanged(getRootNode());
    }

    if (!myLoadingParents.contains(getTreeStructure().getRootElement())) {
      processDeferredActions();
    }
  }

  private boolean update(final NodeDescriptor nodeDescriptor, boolean canBeNonEdt) {
    if (!canBeNonEdt) {
      assertIsDispatchThread();
    }

    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      return getBuilder().updateNodeDescriptor(nodeDescriptor);
    } else {
      app.invokeLater(new Runnable() {
        public void run() {
          if (!isReleased()) {
            getBuilder().updateNodeDescriptor(nodeDescriptor);
          }
        }
      }, ModalityState.stateForComponent(myTree));
      return true;
    }
  }

  private void assertIsDispatchThread() {
    if (myTree.isShowing()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
  }

  private void processDeferredActions() {
    processDeferredActions(myDeferredSelections);
    processDeferredActions(myDeferredExpansions);
  }

  private void processDeferredActions(List<Runnable> actions) {
    final Runnable[] runnables = actions.toArray(new Runnable[actions.size()]);
    actions.clear();
    for (Runnable runnable : runnables) {
      runnable.run();
    }
  }

  public void doUpdateFromRoot() {
    updateSubtree(getRootNode());
  }

  public ActionCallback doUpdateFromRootCB() {
    final ActionCallback cb = new ActionCallback();
    getUpdater().runAfterUpdate(new Runnable() {
      public void run() {
        cb.setDone();
      }
    });
    updateSubtree(getRootNode());
    return cb;
  }

  public final void updateSubtree(DefaultMutableTreeNode node) {
    updateSubtree(new TreeUpdatePass(node));
  }

  public final void updateSubtree(TreeUpdatePass pass) {
    maybeSetBusyAndScheduleWaiterForReady(true);

    initRootNodeNowIfNeeded(pass);

    final DefaultMutableTreeNode node = pass.getNode();

    if (!(node.getUserObject() instanceof NodeDescriptor)) return;

    setUpdaterState(new UpdaterTreeState(this)).beforeSubtreeUpdate();

    getBuilder().updateNode(node);
    updateNodeChildren(node, pass);
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
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    Object prevElement = getElementFromDescriptor(descriptor);
    if (prevElement == null) return;
    boolean changes = update(descriptor, false);
    if (getElementFromDescriptor(descriptor) == null) {
      LOG.assertTrue(false, "element == null, updateSubtree should be invoked for parent! builder=" +
                            getBuilder() +
                            ", prevElement = " +
                            prevElement +
                            ", node = " +
                            node +
                            "; parentDescriptor=" +
                            descriptor.getParentDescriptor());
    }
    if (changes) {
      updateNodeImageAndPosition(node);
    }
  }

  public Object getElementFromDescriptor(NodeDescriptor descriptor) {
    return getBuilder().getTreeStructureElement(descriptor);
  }

  private void updateNodeChildren(final DefaultMutableTreeNode node, final TreeUpdatePass pass) {
    getTreeStructure().commit();
    final boolean wasExpanded = myTree.isExpanded(new TreePath(node.getPath()));
    final boolean wasLeaf = node.getChildCount() == 0;

    final NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();

    if (descriptor == null) return;

    if (myUnbuiltNodes.contains(node)) {
      processUnbuilt(node, descriptor, pass);
      processNodeActionsIfReady(node);
      return;
    }

    if (getTreeStructure().isToBuildChildrenInBackground(getBuilder().getTreeStructureElement(descriptor))) {
      if (queueBackgroundUpdate(node, descriptor, pass)) return;
    }

    final Map<Object, Integer> elementToIndexMap = collectElementToIndexMap(descriptor);

    myUpdatingChildren.add(node);
    processAllChildren(node, elementToIndexMap, pass).doWhenDone(new Runnable() {
      public void run() {
        if (canYield()) {
          removeLoadingNode(node);
        }

        ArrayList<TreeNode> nodesToInsert = collectNodesToInsert(descriptor, elementToIndexMap);

        insertNodesInto(nodesToInsert, node);

        updateNodesToInsert(nodesToInsert, pass);

        if (wasExpanded) {
          expand(node);
        }

        if (wasExpanded || wasLeaf) {
          expand(node, descriptor, wasLeaf);
        }

        myUpdatingChildren.remove(node);

        final Object element = getElementFor(node);
        addNodeAction(element, new NodeAction() {
          public void onReady(final DefaultMutableTreeNode node) {
            removeLoadingNode(node);
          }
        });

        processNodeActionsIfReady(node);
      }
    });
  }

  private void expand(DefaultMutableTreeNode node) {
    expand(new TreePath(node.getPath()));
  }

  private void ____expand(final TreePath path) {
    if (path == null) return;
    final Object last = path.getLastPathComponent();
    final TreePath parent = path.getParentPath();

    if (myTree.isExpanded(path)) {
      if (last instanceof DefaultMutableTreeNode) {
        processNodeActionsIfReady((DefaultMutableTreeNode)last);
      }
    }
    else {
      if (parent != null) {
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)parent.getLastPathComponent();
        final TreePath parentPath = new TreePath(parentNode.getPath());
        if (!myTree.isExpanded(parentPath)) {
          myUnbuiltNodes.add(parentNode);
          expandPath(parentPath);
        }
      }

      expandPath(path);
    }
  }
  private void expand(final TreePath path) {
    if (path == null) return;
    final Object last = path.getLastPathComponent();
    boolean isLeaf = myTree.getModel().isLeaf(path.getLastPathComponent());
    final boolean isRoot = last == myTree.getModel().getRoot();
    final TreePath parent = path.getParentPath();
    if (false) {
      processNodeActionsIfReady((DefaultMutableTreeNode)last);
    }
    else if (myTree.isExpanded(path) || (isLeaf && parent != null && myTree.isExpanded(parent))) {
      if (last instanceof DefaultMutableTreeNode) {
        processNodeActionsIfReady((DefaultMutableTreeNode)last);
      }
    }
    else {
      if (isLeaf && parent != null) {
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)parent.getLastPathComponent();
        if (parentNode != null) {
          myUnbuiltNodes.add(parentNode);
        }
        expandPath(parent);
      }
      else {
        expandPath(path);
      }
    }
  }

  private void processUnbuilt(final DefaultMutableTreeNode node, final NodeDescriptor descriptor, final TreeUpdatePass pass) {
    if (getBuilder().isAlwaysShowPlus(descriptor)) return; // check for isAlwaysShowPlus is important for e.g. changing Show Members state!

    final Object element = getBuilder().getTreeStructureElement(descriptor);

    if (getTreeStructure().isToBuildChildrenInBackground(element)) return; //?

    final Object[] children = getChildrenFor(element);
    if (children.length == 0) {
      removeLoadingNode(node);
    }
    else if (getBuilder().isAutoExpandNode((NodeDescriptor)node.getUserObject())) {
      addNodeAction(getElementFor(node), new NodeAction() {
        public void onReady(final DefaultMutableTreeNode node) {
          final TreePath path = new TreePath(node.getPath());
          if (getTree().isExpanded(path) || children.length == 0) {
            removeLoadingNode(node);
          } else {
            maybeYeild(new ActiveRunnable() {
              public ActionCallback run() {
                expand(element, null);
                return new ActionCallback.Done();
              }
            }, pass, node);
          }
        }
      });
    }
  }

  private void removeLoadingNode(final DefaultMutableTreeNode parent) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      if (removeIfLoading(parent.getChildAt(i))) break;
    }
    myUnbuiltNodes.remove(parent);
  }

  private boolean removeIfLoading(TreeNode node) {
    if (isLoadingNode(node)) {
      removeNodeFromParent((MutableTreeNode)node, false);
      return true;
    }

    return false;
  }

  //todo [kirillk] temporary consistency check
  private Object[] getChildrenFor(final Object element) {
    final Object[] passOne;
    try {
      passOne = getTreeStructure().getChildElements(element);
    }
    catch (IndexNotReadyException e) {
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

  private void updateNodesToInsert(final ArrayList<TreeNode> nodesToInsert, TreeUpdatePass pass) {
    for (TreeNode aNodesToInsert : nodesToInsert) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)aNodesToInsert;
      addLoadingNode(childNode);
      updateNodeChildren(childNode, pass);
    }
  }

  private ActionCallback processAllChildren(final DefaultMutableTreeNode node,
                                            final Map<Object, Integer> elementToIndexMap,
                                            final TreeUpdatePass pass) {

    final ArrayList<TreeNode> childNodes = TreeUtil.childrenToArray(node);

    return maybeYeild(new ActiveRunnable() {
      public ActionCallback run() {
        return processAllChildren(node, elementToIndexMap, pass, childNodes);
      }
    }, pass, node);
  }

  private ActionCallback maybeYeild(final ActiveRunnable processRunnable, final TreeUpdatePass pass, final DefaultMutableTreeNode node) {
    final ActionCallback result = new ActionCallback();

    if (isToYieldUpdateFor(node)) {
      yieldAndRun(new Runnable() {
        public void run() {
          if (pass.isExpired()) return;

          if (pass.getUpdateStamp() < getUpdater().getUpdateCount()) {
            getUpdater().addSubtreeToUpdate(pass);
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

        runOnYeildingDone(new Runnable() {
          public void run() {
            if (isReleased()) {
              return;
            }
            executeYeildingRequest(runnable, pass);
          }
        });
      }
    });
  }

  public boolean isYeildingNow() {
    return myYeildingNow;
  }

  private boolean hasSheduledUpdates() {
    return getUpdater().hasNodesToUpdate() || myLoadingParents.size() > 0;
  }

  private boolean hasExpandedUnbuiltNodes() {
    for (DefaultMutableTreeNode each : myUnbuiltNodes) {
      if (myTree.isExpanded(new TreePath(each.getPath()))) return true;
    }

    return false;
  }

  public boolean isReady() {
    return !isYeildingNow() && !hasSheduledUpdates() && !hasExpandedUnbuiltNodes();
  }

  private void executeYeildingRequest(Runnable runnable, TreeUpdatePass pass) {
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
  }

  protected void runOnYeildingDone(Runnable onDone) {
    getBuilder().runOnYeildingDone(onDone);
  }

  protected void yield(Runnable runnable) {
    getBuilder().yield(runnable);
  }

  private ActionCallback processAllChildren(final DefaultMutableTreeNode node,
                                  final Map<Object, Integer> elementToIndexMap,
                                  final TreeUpdatePass pass,
                                  final ArrayList<TreeNode> childNodes) {


    if (pass.isExpired()) return new ActionCallback.Rejected();

    if (childNodes.size() == 0) return new ActionCallback.Done();

    
    final ActionCallback result = new ActionCallback(childNodes.size());

    for (TreeNode childNode1 : childNodes) {
      final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
      if (isLoadingNode(childNode)) {
        result.setDone();
        continue;
      }

      maybeYeild(new ActiveRunnable() {
        @Override
        public ActionCallback run() {
          return processChildNode(childNode, (NodeDescriptor)childNode.getUserObject(), node, elementToIndexMap, pass);
        }
      }, pass, node).notify(result);
    }

    return result;
  }

  private boolean isToYieldUpdateFor(final DefaultMutableTreeNode node) {
    if (!canYield()) return false;
    return getBuilder().isToYieldUpdateFor(node);
  }

  private Map<Object, Integer> collectElementToIndexMap(final NodeDescriptor descriptor) {
    Map<Object, Integer> elementToIndexMap = new LinkedHashMap<Object, Integer>();
    Object[] children = getChildrenFor(getBuilder().getTreeStructureElement(descriptor));
    int index = 0;
    for (Object child : children) {
      if (!isValid(child)) continue;
      elementToIndexMap.put(child, Integer.valueOf(index));
      index++;
    }
    return elementToIndexMap;
  }

  private void expand(final DefaultMutableTreeNode node, final NodeDescriptor descriptor, final boolean wasLeaf) {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    alarm.addRequest(new Runnable() {
      public void run() {
        myTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }, WAIT_CURSOR_DELAY);

    if (wasLeaf && getBuilder().isAutoExpandNode(descriptor)) {
      expand(node);
    }

    ArrayList<TreeNode> nodes = TreeUtil.childrenToArray(node);
    for (TreeNode node1 : nodes) {
      final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node1;
      if (isLoadingNode(childNode)) continue;
      NodeDescriptor childDescr = (NodeDescriptor)childNode.getUserObject();
      if (getBuilder().isAutoExpandNode(childDescr)) {
        addNodeAction(getElementFor(childNode), new NodeAction() {
          public void onReady(DefaultMutableTreeNode node) {
            expand(childNode);
          }
        });
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

  private ArrayList<TreeNode> collectNodesToInsert(final NodeDescriptor descriptor, final Map<Object, Integer> elementToIndexMap) {
    ArrayList<TreeNode> nodesToInsert = new ArrayList<TreeNode>();
    for (Map.Entry<Object, Integer> entry : elementToIndexMap.entrySet()) {
      Object child = entry.getKey();
      Integer index = entry.getValue();
      final NodeDescriptor childDescr = getTreeStructure().createDescriptor(child, descriptor);
      //noinspection ConstantConditions
      if (childDescr == null) {
        LOG.error("childDescr == null, treeStructure = " + getTreeStructure() + ", child = " + child);
        continue;
      }
      childDescr.setIndex(index.intValue());
      update(childDescr, false);
      if (getElementFromDescriptor(childDescr) == null) {
        LOG.error("childDescr.getElement() == null, child = " + child + ", builder = " + this);
        continue;
      }
      final DefaultMutableTreeNode childNode = createChildNode(childDescr);
      nodesToInsert.add(childNode);
      createMapping(getElementFromDescriptor(childDescr), childNode);
    }
    return nodesToInsert;
  }

  protected DefaultMutableTreeNode createChildNode(final NodeDescriptor descriptor) {
    return new ElementNode(this, descriptor);
  }

  protected boolean canYield() {
    return myCanYield && myYeildingUpdate.asBoolean();
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
  }

  private boolean isUpdatingParent(DefaultMutableTreeNode kid) {
    DefaultMutableTreeNode eachParent = kid;
    while (eachParent != null) {
      if (myUpdatingChildren.contains(eachParent)) return true;
      eachParent = (DefaultMutableTreeNode)eachParent.getParent();
    }

    return false;
  }

  private boolean queueBackgroundUpdate(final DefaultMutableTreeNode node, final NodeDescriptor descriptor, final TreeUpdatePass pass) {
    if (myLoadingParents.contains(getElementFromDescriptor(descriptor))) return false;

    myLoadingParents.add(getElementFromDescriptor(descriptor));

    if (!isNodeBeingBuilt(node)) {
      LoadingNode loadingNode = new LoadingNode(getLoadingNodeText());
      myTreeModel.insertNodeInto(loadingNode, node, node.getChildCount());
    }

    Runnable updateRunnable = new Runnable() {
      public void run() {
        if (isReleased()) return;

        update(descriptor, true);
        Object element = getElementFromDescriptor(descriptor);
        if (element == null) return;

        getChildrenFor(getBuilder().getTreeStructureElement(descriptor)); // load children
      }
    };

    Runnable postRunnable = new Runnable() {
      public void run() {
        if (isReleased()) return;

        updateNodeChildren(node, pass);

        myLoadingParents.remove(getElementFromDescriptor(descriptor));

        update(descriptor, false);
        Object element = getElementFromDescriptor(descriptor);

        if (element != null) {
          myUnbuiltNodes.remove(node);

          for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode child = node.getChildAt(i);
            if (isLoadingNode(child)) {
              if (TreeBuilderUtil.isNodeSelected(myTree, node)) {
                addSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)), true, Condition.FALSE);
              }
              removeIfLoading(child);
              i--;
            }
          }

          processNodeActionsIfReady(node);
        }
      }
    };
    addTaskToWorker(updateRunnable, true, postRunnable);
    return true;
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

    final List<NodeAction> actions = myNodeActions.get(element);
    if (actions != null) {
      myNodeActions.remove(element);
      for (NodeAction each : actions) {
        each.onReady(node);
      }
    }

    if (!isUpdatingParent(node)) {
      //if (myUpdaterState != null) {
      //  if (myUpdaterState.process(node, myTree)) {
      //    clearUpdaterState();
      //  }
      //}

      final UpdaterTreeState state = myUpdaterState;
      if (myNodeActions.size() == 0 && state != null && !state.isProcessingNow()) {
        if (!state.restore()) {
          setUpdaterState(state);
        }
      }
    }
  }

  private void processSmartExpand(final DefaultMutableTreeNode node) {
    if (getBuilder().isSmartExpand() && node.getChildCount() == 1) { // "smart" expand
      TreeNode childNode = node.getChildAt(0);
      if (isLoadingNode(childNode)) return;
      final TreePath childPath = new TreePath(node.getPath()).pathByAddingChild(childNode);
      expand(childPath);
    }
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
        if (myLoadingParents.contains(eachElement)) return true;
      }
    }

    return false;
  }

  protected String getLoadingNodeText() {
    return IdeBundle.message("progress.searching");
  }

  private ActionCallback processChildNode(final DefaultMutableTreeNode childNode,
                                final NodeDescriptor childDescr,
                                final DefaultMutableTreeNode node,
                                final Map<Object, Integer> elementToIndexMap,
                                TreeUpdatePass pass) {

    if (pass.isExpired()) {
      return new ActionCallback.Rejected();
    }


    if (childDescr == null) {
      pass.expire();
      return new ActionCallback.Rejected();
    }
    Object oldElement = getElementFromDescriptor(childDescr);
    if (oldElement == null) {
      pass.expire();
      return new ActionCallback.Rejected();
    }
    boolean changes = update(childDescr, false);
    Object newElement = getElementFromDescriptor(childDescr);
    Integer index = newElement != null ? elementToIndexMap.get(getBuilder().getTreeStructureElement(childDescr)) : null;
    if (index != null) {
      if (childDescr.getIndex() != index.intValue()) {
        changes = true;
      }
      childDescr.setIndex(index.intValue());
    }
    if (index != null && changes) {
      updateNodeImageAndPosition(childNode);
    }
    if (!oldElement.equals(newElement)) {
      removeMapping(oldElement, childNode);
      if (newElement != null) {
        createMapping(newElement, childNode);
      }
    }

    if (index == null) {
      int selectedIndex = -1;
      if (TreeBuilderUtil.isNodeOrChildSelected(myTree, childNode)) {
        selectedIndex = node.getIndex(childNode);
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

      if (selectedIndex >= 0) {
        if (node.getChildCount() > 0) {
          if (node.getChildCount() > selectedIndex) {
            TreeNode newChildNode = node.getChildAt(selectedIndex);
            if (isValidForSelectionAdjusting(newChildNode)) {
              addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChildNode)), true, getExpiredElementCondition(disposedElement));
            }
          }
          else {
            TreeNode newChild = node.getChildAt(node.getChildCount() - 1);
            if (isValidForSelectionAdjusting(newChild)) {
              addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChild)), true, getExpiredElementCondition(disposedElement));
            }
          }
        }
        else {
          addSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)), true, getExpiredElementCondition(disposedElement));
        }
      }
    }
    else {
      elementToIndexMap.remove(getBuilder().getTreeStructureElement(childDescr));
      updateNodeChildren(childNode, pass);
    }

    if (node.equals(getRootNode())) {
      myTreeModel.nodeChanged(getRootNode());
    }

    return new ActionCallback.Done();
  }

  private boolean isValidForSelectionAdjusting(TreeNode node) {
    if (isLoadingNode(node)) return true;

    final Object elementInTree = getElementFor(node);
    if (elementInTree == null) return false;

    final TreeNode parentNode = node.getParent();
    final Object parentElementInTree = getElementFor(parentNode);
    if (parentElementInTree == null) return false;

    final Object parentElement = getTreeStructure().getParentElement(elementInTree);

    return parentElementInTree.equals(parentElement);
  }

  private Condition getExpiredElementCondition(final Object element) {
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

  private void expandPath(final TreePath path) {
    doWithUpdaterState(new Runnable() {
      public void run() {
        myTree.expandPath(path);
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

  private void addLoadingNode(final DefaultMutableTreeNode node) {
    final NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    if (!getBuilder().isAlwaysShowPlus(descriptor)) {
      if (getTreeStructure().isToBuildChildrenInBackground(getBuilder().getTreeStructureElement(descriptor))) {
        final boolean[] hasNoChildren = new boolean[1];
        Runnable runnable = new Runnable() {
          public void run() {
            if (isReleased()) return;

            update(descriptor, true);
            Object element = getBuilder().getTreeStructureElement(descriptor);
            if (element == null && !isValid(element)) return;

            Object[] children = getChildrenFor(element);
            hasNoChildren[0] = children.length == 0;
          }
        };

        Runnable postRunnable = new Runnable() {
          public void run() {
            if (isReleased()) return;

            if (hasNoChildren[0]) {
              update(descriptor, false);
              // todo please check the fix
              // removing loading node from the same node we've added it to, no need to look for it
              removeLoadingNode(node);
              //Object element = getElementFromDescriptor(descriptor);
              //if (element != null) {
              //  DefaultMutableTreeNode node = getNodeForElement(element, false);
              //  removeLoadingNode(node);
              //}
            }
          }
        };

        addTaskToWorker(runnable, false, postRunnable);
      }
      else {
        Object[] children = getChildrenFor(getBuilder().getTreeStructureElement(descriptor));
        if (children.length == 0) return;
      }
    }

    insertLoadingNode(node, true);
  }

  private boolean isValid(Object element) {
    if (element instanceof ValidateableNode) {
      if (!((ValidateableNode)element).isValid()) return false;
    }
    return getBuilder().validateNode(element);
  }

  private void insertLoadingNode(final DefaultMutableTreeNode node, boolean addToUnbuilt) {
    myTreeModel.insertNodeInto(new LoadingNode(), node, 0);
    if (addToUnbuilt) {
      myUnbuiltNodes.add(node);
    }
  }

  protected void addTaskToWorker(final Runnable runnable, boolean first, final Runnable postRunnable) {
    Runnable runnable1 = new Runnable() {
      public void run() {
        if (isReleased()) return;

        try {
          Runnable runnable2 = new Runnable() {
            public void run() {
              if (isReleased()) return;

              final Application app = ApplicationManager.getApplication();
              if (app == null) return;

              app.runReadAction(runnable);
              if (postRunnable != null) {
                final JTree tree = myTree;
                if (tree != null && tree.isVisible()) {
                  app.invokeLater(postRunnable, ModalityState.stateForComponent(tree));
                }
                else {
                  app.invokeLater(postRunnable);
                }
              }
            }
          };
          if (myProgress != null) {
            ProgressManager.getInstance().runProcess(runnable2, myProgress);
          }
          else {
            runnable2.run();
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
        myWorker.addTaskFirst(runnable1);
      }
      else {
        myWorker.addTask(runnable1);
      }
      myWorker.dispose(false);
    }
    else {
      if (first) {
        myWorker.addTaskFirst(runnable1);
      }
      else {
        myWorker.addTask(runnable1);
      }
    }
  }

  private void updateNodeImageAndPosition(final DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    if (getElementFromDescriptor(descriptor) == null) return;
    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)node.getParent();
    if (parentNode != null) {
      int oldIndex = parentNode.getIndex(node);
      int newIndex = oldIndex;
      if (isLoadingChildrenFor(node.getParent()) || getBuilder().isChildrenResortingNeeded(descriptor)) {
        final ArrayList<TreeNode> children = new ArrayList<TreeNode>(parentNode.getChildCount());
        for (int i = 0; i < parentNode.getChildCount(); i++) {
          children.add(parentNode.getChildAt(i));
        }
        Collections.sort(children, myNodeComparator);
        newIndex = children.indexOf(node);
      }

      if (oldIndex != newIndex) {
        List<Object> pathsToExpand = new ArrayList<Object>();
        List<Object> selectionPaths = new ArrayList<Object>();
        TreeBuilderUtil.storePaths(getBuilder(), node, pathsToExpand, selectionPaths, false);
        removeNodeFromParent(node, false);
        myTreeModel.insertNodeInto(node, parentNode, newIndex);
        TreeBuilderUtil.restorePaths(getBuilder(), pathsToExpand, selectionPaths, false);
      }
      else {
        myTreeModel.nodeChanged(node);
      }
    }
    else {
      myTreeModel.nodeChanged(node);
    }
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  private void insertNodesInto(ArrayList<TreeNode> nodes, DefaultMutableTreeNode parentNode) {
    if (nodes.isEmpty()) return;

    nodes = new ArrayList<TreeNode>(nodes);
    Collections.sort(nodes, myNodeComparator);

    ArrayList<TreeNode> all = TreeUtil.childrenToArray(parentNode);
    all.addAll(nodes);
    Collections.sort(all, myNodeComparator);

    int[] indices = new int[nodes.size()];
    int idx = 0;
    for (int i = 0; i < nodes.size(); i++) {
      TreeNode node = nodes.get(i);
      while (all.get(idx) != node) idx++;
      indices[i] = idx;
      parentNode.insert((MutableTreeNode)node, idx);
    }

    myTreeModel.nodesWereInserted(parentNode, indices);
  }

  private void disposeNode(DefaultMutableTreeNode node) {
    if (node.getChildCount() > 0) {
      for (DefaultMutableTreeNode _node = (DefaultMutableTreeNode)node.getFirstChild(); _node != null; _node = _node.getNextSibling()) {
        disposeNode(_node);
      }
    }
    if (isLoadingNode(node)) return;
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    if (descriptor == null) return;
    final Object element = getElementFromDescriptor(descriptor);
    removeMapping(element, node);
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

  public void select(final Object[] elements, @Nullable final Runnable onDone) {
    select(elements, onDone, false);
  }

  public void select(final Object[] elements, @Nullable final Runnable onDone, boolean addToSelection) {
    _select(elements, onDone, addToSelection, true, false);
  }

  void _select(final Object[] elements,
               final Runnable onDone,
               final boolean addToSelection,
               final boolean checkCurrentSelection,
               final boolean checkIfInStructure) {


    runDone(new Runnable() {
      public void run() {
        if (elements.length == 0) {
          runDone(onDone);
          return;
        }

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
            runDone(onDone);
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
          addNext(elementsToSelect, 0, onDone, originalRows);
        }
        else {
          myDeferredSelections.clear();
          myDeferredSelections.add(new Runnable() {
            public void run() {
              select(elementsToSelect, onDone);
            }
          });
        }
      }
    });
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


  private void addNext(final Object[] elements, final int i, @Nullable final Runnable onDone, final int[] originalRows) {
    if (i >= elements.length) {
      if (myTree.isSelectionEmpty()) {
        myTree.setSelectionRows(originalRows);
      }
      runDone(onDone);
    }
    else {
      _select(elements[i], new Runnable() {
        public void run() {
          addNext(elements, i + 1, onDone, originalRows);
        }
      }, true);
    }
  }

  public void select(final Object element, @Nullable final Runnable onDone) {
    select(element, onDone, false);
  }

  public void select(final Object element, @Nullable final Runnable onDone, boolean addToSelection) {
    _select(element, onDone, addToSelection);
  }

  private void _select(final Object element, final Runnable onDone, final boolean addToSelection) {
    final Runnable _onDone = new Runnable() {
      public void run() {
        final DefaultMutableTreeNode toSelect = getNodeForElement(element, false);
        if (toSelect == null) {
          runDone(onDone);
          return;
        }
        final int row = myTree.getRowForPath(new TreePath(toSelect.getPath()));

        if (myUpdaterState != null) {
          myUpdaterState.addSelection(element);
        }
        TreeUtil.showAndSelect(myTree, row - 2, row + 2, row, -1, addToSelection);
        runDone(onDone);
      }
    };
    _expand(element, _onDone, true, false);
  }

  public void expand(final Object element, @Nullable final Runnable onDone) {
    expand(element, onDone, false);
  }


  void expand(final Object element, @Nullable final Runnable onDone, boolean checkIfInStructure) {
    _expand(new Object[]{element}, onDone == null ? new EmptyRunnable() : onDone, false, checkIfInStructure);
  }

  void expand(final Object[] element, @Nullable final Runnable onDone, boolean checkIfInStructure) {
    _expand(element, onDone == null ? new EmptyRunnable() : onDone, false, checkIfInStructure);
  }

  void _expand(final Object[] element, @NotNull final Runnable onDone, final boolean parentsOnly, final boolean checkIfInStructure) {
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
          }, parentsOnly, checkIfInStructure);
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
    if (done == null) return;

    if (isYeildingNow()) {
      if (!myYeildingDoneRunnables.contains(done)) {
        myYeildingDoneRunnables.add(done);
      }
    } else {
      done.run();
    }
  }

  private void _expand(final Object element, @NotNull final Runnable onDone, final boolean parentsOnly, boolean checkIfInStructure) {
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
            expand(parentPath);
          }
        }
        runDone(onDone);
      }
      else {
        processExpand(firstVisible, kidsToExpand, kidsToExpand.size() - 1, onDone);
      }
    }
    else {
      myDeferredExpansions.add(new Runnable() {
        public void run() {
          _expand(element, onDone, parentsOnly, false);
        }
      });
    }
  }

  private void processExpand(final DefaultMutableTreeNode toExpand,
                             final List kidsToExpand,
                             final int expandIndex,
                             @NotNull final Runnable onDone) {
    final Object element = getElementFor(toExpand);
    if (element == null) return;

    addNodeAction(element, new NodeAction() {
      public void onReady(final DefaultMutableTreeNode node) {
        if (node.getChildCount() >= 0 && !myTree.isExpanded(new TreePath(node.getPath()))) {
          expand(node);
        }

        if (expandIndex < 0) {
          runDone(onDone);
          return;
        }

        final DefaultMutableTreeNode nextNode = getNodeForElement(kidsToExpand.get(expandIndex), false);
        if (nextNode != null) {
          processExpand(nextNode, kidsToExpand, expandIndex - 1, onDone);
        }
        else {
          runDone(onDone);
        }
      }
    });

    expand(toExpand);
  }


  @Nullable
  private Object getElementFor(Object node) {
    if (!(node instanceof DefaultMutableTreeNode)) return null;
    return getElementFor((DefaultMutableTreeNode)node);
  }

  @Nullable
  private Object getElementFor(DefaultMutableTreeNode node) {
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

    final boolean childrenAreNoLoadedYet = isLoadingChildrenFor(node) && myUnbuiltNodes.contains(node);
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
    return myLoadingParents.contains(getElementFor((DefaultMutableTreeNode)node));
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

  private void clearUpdaterState() {
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

  private void removeMapping(Object element, DefaultMutableTreeNode node) {
    final Object value = myElementToNodeMap.get(element);
    if (value == null) {
      return;
    }
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
    }
    myNodeActions.clear();
  }

  private void addNodeAction(Object element, NodeAction action) {
    maybeSetBusyAndScheduleWaiterForReady(true);

    List<NodeAction> list = myNodeActions.get(element);
    if (list == null) {
      list = new ArrayList<NodeAction>();
      myNodeActions.put(element, list);
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
    myNodeActions.clear();
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

    state.restore();
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
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (!myUnbuiltNodes.contains(node)) return;
      myUnbuiltNodes.remove(node);

      getBuilder().expandNodeChildren(node);

      runDone(new Runnable() {
        public void run() {
          final Object element = getElementFor(node);

          for (int i = 0; i < node.getChildCount(); i++) {
            removeIfLoading(node.getChildAt(i));
          }

          if (node.getChildCount() == 0) {
            addNodeAction(element, new NodeAction() {
              public void onReady(final DefaultMutableTreeNode node) {
                expand(element, null);
              }
            });
          }

          processSmartExpand(node);
        }
      });
    }

    public void treeCollapsed(TreeExpansionEvent e) {
      final TreePath path = e.getPath();
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (!(node.getUserObject() instanceof NodeDescriptor)) return;

      TreePath pathToSelect = null;
      if (isSelectionInside(node)) {
        pathToSelect = new TreePath(node.getPath());
      }


      NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
      if (getBuilder().isDisposeOnCollapsing(descriptor)) {
        removeChildren(node);
        addLoadingNode(node);
        if (node.equals(getRootNode())) {
          addSelectionPath(new TreePath(getRootNode().getPath()), true, Condition.FALSE);
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
}
