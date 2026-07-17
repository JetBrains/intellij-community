// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.console.BuildConsoleViewHandler;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.DerivedResult;
import com.intellij.build.events.DuplicateMessageAware;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.Failure;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.MessageEventResult;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.PresentableBuildEvent;
import com.intellij.build.events.ProgressBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.StartEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioPathUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.render.RenderingHelper;
import com.intellij.ui.split.SplitComponentBindingKt;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Shape;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public final class BuildTreeConsoleView
  implements ConsoleView, UiDataProvider, BuildConsoleView, Filterable<ExecutionNode>, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance(BuildTreeConsoleView.class);
  @ApiStatus.Internal
  public static final DataKey<BuildTreeConsoleView> COMPONENT_KEY = DataKey.create("BuildTreeConsoleView");
  public static final DataKey<ExecutionNode> EXECUTION_NODE = DataKey.create("ExecutionNode");

  private static final @NonNls String TREE = "tree";
  @ApiStatus.Internal
  public static final @NonNls String SPLITTER_PROPERTY = "BuildView.Splitter.Proportion";
  @ApiStatus.Internal
  public static final float SPLITTER_DEFAULT_PROPORTION = 0.33f;

  @Service(Service.Level.PROJECT)
  private static final class ScopeHolder {
    private final CoroutineScope scope;

    private ScopeHolder(CoroutineScope scope) {
      this.scope = scope;
    }

    public static CoroutineScope getScope(Project project) {
      return project.getService(ScopeHolder.class).scope;
    }
  }

  private final JPanel myPanel = new JPanel();
  private final Map<Object, ExecutionNode> nodesMap = new ConcurrentHashMap<>();

  private final @NotNull Project myProject;
  private final @NotNull DefaultBuildDescriptor myBuildDescriptor;
  private final @NotNull String myWorkingDir;
  private final BuildConsoleViewHandler myConsoleViewHandler;
  private final AtomicBoolean myFinishedBuildEventReceived = new AtomicBoolean();
  private final AtomicBoolean myDisposed = new AtomicBoolean();
  private final AtomicBoolean myShownFirstError = new AtomicBoolean();
  private final AtomicBoolean myExpandedFirstMessage = new AtomicBoolean();
  private final boolean myNavigateToTheFirstErrorLocation;
  private final Invoker myInvoker;
  private final StructureTreeModel<AbstractTreeStructure> myTreeModel;
  private final Tree myTree;
  private final CoroutineScope myScope;
  private final BuildTreeViewModel myTreeVm;
  private final JComponent mySplitComponent;
  private final @NotNull ExecutionNode myRootNode;
  private final @NotNull ExecutionNode myBuildProgressRootNode;
  private final Set<Predicate<? super ExecutionNode>> myNodeFilters;
  private final OccurenceNavigator myOccurrenceNavigatorSupport;
  private final Set<BuildEvent> myDeferredEvents = ConcurrentCollectionFactory.createConcurrentSet();

  private final boolean mySplitImplementation = Registry.is("build.toolwindow.split.tree", false) ||
                                                Registry.is("build.toolwindow.split", false);

  public BuildTreeConsoleView(@NotNull Project project,
                              @NotNull BuildDescriptor buildDescriptor,
                              @Nullable ExecutionConsole executionConsole) {
    myProject = project;
    myBuildDescriptor = buildDescriptor instanceof DefaultBuildDescriptor
                        ? (DefaultBuildDescriptor)buildDescriptor
                        : new DefaultBuildDescriptor(buildDescriptor);
    myNodeFilters = ConcurrentCollectionFactory.createConcurrentSet();
    myWorkingDir = FileUtil.toSystemIndependentName(buildDescriptor.getWorkingDir());
    myNavigateToTheFirstErrorLocation = isNavigateToTheFirstErrorLocation(project, buildDescriptor);

    myRootNode = new ExecutionNode(myProject, null, true, this::isCorrectThread);
    myBuildProgressRootNode = new ExecutionNode(myProject, myRootNode, true, this::isCorrectThread);
    if (!mySplitImplementation) {
      myRootNode.setFilter(getFilter());
    }
    myRootNode.add(myBuildProgressRootNode);

    myInvoker = Invoker.forBackgroundThreadWithoutReadAction(this);

    JComponent treeComponent;
    if (mySplitImplementation) {
      myScope = CoroutineScopeKt.childScope(ScopeHolder.getScope(project), "BuildTreeConsoleView", EmptyCoroutineContext.INSTANCE, true);
      myTreeVm = new BuildTreeViewModel(this, myScope);
      mySplitComponent = SplitComponentBindingKt.createComponent(
        BuildTreeSplitComponentBindingKt.getBuildTreeSplitComponentBinding(),
        myProject, myScope, myTreeVm.getId()
      );

      treeComponent = mySplitComponent;

      myOccurrenceNavigatorSupport = new SplitProblemOccurrenceNavigatorSupport(myTreeVm);

      myTreeModel = null;
      myTree = null;
    }
    else {
      AbstractTreeStructure treeStructure = new MyTreeStructure();
      myTreeModel = new StructureTreeModel<>(treeStructure, null, myInvoker, this);
      AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myTreeModel, this);
      asyncTreeModel.addTreeModelListener(new ExecutionNodeAutoExpandingListener());
      myTree = initTree(asyncTreeModel);
      myTree.getAccessibleContext().setAccessibleName(IdeBundle.message("buildToolWindow.tree.accessibleName"));

      JPanel myContentPanel = new JPanel();
      myContentPanel.setLayout(new CardLayout());
      myContentPanel.add(ScrollPaneFactory.createScrollPane(myTree, SideBorder.NONE), TREE);

      if (ExperimentalUI.isNewUI()) {
        UIUtil.setBackgroundRecursively(myContentPanel, JBUI.CurrentTheme.ToolWindow.background());
      }

      treeComponent = myContentPanel;

      myOccurrenceNavigatorSupport = new ProblemOccurrenceNavigatorSupport(myTree);

      myScope = null;
      myTreeVm = null;
      mySplitComponent = null;
    }

    myPanel.setLayout(new BorderLayout());
    OnePixelSplitter myThreeComponentsSplitter = new OnePixelSplitter(SPLITTER_PROPERTY, SPLITTER_DEFAULT_PROPORTION);
    myThreeComponentsSplitter.setFirstComponent(treeComponent);
    List<Filter> filters = myBuildDescriptor.getExecutionFilters();
    myConsoleViewHandler = new BuildConsoleViewHandler(myProject, myTree, myBuildProgressRootNode, this, executionConsole, filters);
    myThreeComponentsSplitter.setSecondComponent(myConsoleViewHandler.getComponent());
    myPanel.add(myThreeComponentsSplitter, BorderLayout.CENTER);
    BuildTreeFilters.install(this);
  }

  private static boolean isNavigateToTheFirstErrorLocation(@NotNull Project project, @NotNull BuildDescriptor buildDescriptor) {
    ThreeState isNavigateToError =
      buildDescriptor instanceof DefaultBuildDescriptor
      ? ((DefaultBuildDescriptor)buildDescriptor).isNavigateToError()
      : ThreeState.UNSURE;
    BuildWorkspaceConfiguration workspaceConfiguration = BuildWorkspaceConfiguration.getInstance(project);
    return isNavigateToError == ThreeState.UNSURE
           ? workspaceConfiguration.isShowFirstErrorInEditor()
           : isNavigateToError.toBoolean();
  }

  boolean isCorrectThread() {
    if (myInvoker != null) {
      return myInvoker.isValidThread();
    }
    return true;
  }

  @NotNull
  private Function<@Nullable ExecutionNode, @NotNull ActionGroup> getMainContextMenuGroupSupplier() {
    List<AnAction> actions = new ArrayList<>(myBuildDescriptor.getRestartActions());
    if (!actions.isEmpty()) {
      actions.add(Separator.getInstance());
    }

    EditSourceAction edit = new EditSourceAction();
    ActionUtil.copyFrom(edit, "EditSource");
    actions.add(edit);

    return node -> {
      DefaultActionGroup group = new DefaultActionGroup();
      group.addAll(actions);
      if (node != null) {
        List<AnAction> contextActions = myBuildDescriptor.getContextActions(node);
        if (!contextActions.isEmpty()) {
          group.addSeparator();
          group.addAll(contextActions);
        }
      }
      return group;
    };
  }

  @ApiStatus.Internal
  public ActionGroup getFilteringAndNavigationContextMenuGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    Supplier<Filterable<ExecutionNode>> supplier = new WeakFilterableSupplier<>(this);
    group.add(new WarningsToggleAction(supplier));
    group.add(new SuccessfulStepsToggleAction(supplier));
    group.addSeparator();

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    group.add(actionsManager.createPrevOccurenceAction(this));
    group.add(actionsManager.createNextOccurenceAction(this));
    return group;
  }

  private void installContextMenu() {
    if (myTree == null) return;
    UIUtil.invokeLaterIfNeeded(() -> {
      Function<ExecutionNode, ActionGroup> supplier = getMainContextMenuGroupSupplier();
      ActionGroup viewGroup = getFilteringAndNavigationContextMenuGroup();
      myTree.addMouseListener(new PopupHandler() {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          ExecutionNode[] selectedNodes = getSelectedNodes();
          ExecutionNode selectedNode = selectedNodes.length == 1 ? selectedNodes[0] : null;

          DefaultActionGroup group = new DefaultActionGroup();
          group.add(supplier.apply(selectedNode));
          group.addSeparator();
          group.add(viewGroup);

          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("BuildView", group);
          popupMenu.setTargetComponent(myTree);
          JPopupMenu menu = popupMenu.getComponent();
          menu.show(comp, x, y);
        }
      });
    });
  }

  @ApiStatus.Internal
  public ActionGroup getMainContextMenuGroup(DataContext context) {
    SelectedBuildTreeNode selectedNode = context.getData(BuildTreeContextKt.getBUILD_TREE_SELECTED_NODE());
    ExecutionNode node = selectedNode == null ? null : myTreeVm.getNodeById(selectedNode.getNodeId());
    return getMainContextMenuGroupSupplier().apply(node);
  }

  @Override
  public void clear() {
    myInvoker.invoke(() -> {
      getRootElement().removeChildren();
      nodesMap.clear();
      myConsoleViewHandler.clear();
      if (mySplitImplementation) {
        myTreeVm.clearNodes();
      }
    });
    scheduleUpdate(getRootElement(), true);
  }

  boolean isAlwaysVisible(@NotNull ExecutionNode node) {
    return isBuildProgressRootNode(node) || node.isRunning() || node.isFailed();
  }

  @Override
  public boolean isFilteringEnabled() {
    return true;
  }

  @Override
  public @NotNull Predicate<ExecutionNode> getFilter() {
    return executionNode -> isAlwaysVisible(executionNode) ||
                            ContainerUtil.exists(myNodeFilters, predicate -> predicate.test(executionNode));
  }

  @Override
  public void addFilter(@NotNull Predicate<? super ExecutionNode> executionTreeFilter) {
    if (mySplitImplementation) {
      if (executionTreeFilter == BuildTreeFilters.getSUCCESSFUL_STEPS_FILTER()) {
        myTreeVm.setShowingSuccessful(true);
      }
      else if (executionTreeFilter == BuildTreeFilters.getWARNINGS_FILTER()) {
        myTreeVm.setShowingWarnings(true);
      }
      else {
        LOG.error("Unknown filter: " + executionTreeFilter);
      }
    }
    else {
      myNodeFilters.add(executionTreeFilter);
      updateFilter();
    }
  }

  @Override
  public void removeFilter(@NotNull Predicate<? super ExecutionNode> filter) {
    if (mySplitImplementation) {
      if (filter == BuildTreeFilters.getSUCCESSFUL_STEPS_FILTER()) {
        myTreeVm.setShowingSuccessful(false);
      }
      else if (filter == BuildTreeFilters.getWARNINGS_FILTER()) {
        myTreeVm.setShowingWarnings(false);
      }
      else {
        LOG.error("Unknown filter: " + filter);
      }
    }
    else {
      myNodeFilters.remove(filter);
      updateFilter();
    }
  }

  @Override
  public boolean contains(@NotNull Predicate<? super ExecutionNode> filter) {
    if (mySplitImplementation) {
      if (filter == BuildTreeFilters.getSUCCESSFUL_STEPS_FILTER()) {
        return myTreeVm.getShowingSuccessful();
      }
      else if (filter == BuildTreeFilters.getWARNINGS_FILTER()) {
        return myTreeVm.getShowingWarnings();
      }
      else {
        LOG.error("Unknown filter: " + filter);
        return false;
      }
    }
    else {
      return myNodeFilters.contains(filter);
    }
  }

  private void updateFilter() {
    if (mySplitImplementation) return;
    ExecutionNode rootElement = getRootElement();
    myInvoker.invoke(() -> {
      rootElement.setFilter(getFilter());
      scheduleUpdate(rootElement, true);
    });
  }

  @NotNull ExecutionNode getRootElement() {
    return myRootNode;
  }

  @NotNull ExecutionNode getBuildProgressRootNode() {
    return myBuildProgressRootNode;
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
  }

  private void onEventInternal(@NotNull BuildEvent event) {
    switch (event) {
      case StartEvent startEvent -> onStartEvent(startEvent);
      case FinishEvent finishEvent -> onFinishEvent(finishEvent);
      case ProgressBuildEvent progressEvent -> onProgressEvent(progressEvent);
      case MessageEvent messageEvent -> onMessageEvent(messageEvent);
      case OutputBuildEvent outputEvent -> onOutputEvent(outputEvent);
      case PresentableBuildEvent presentableEvent -> onPresentableEvent(presentableEvent);
      default -> onBuildEvent(event);
    }
  }

  private void onStartEvent(@NotNull StartEvent event) {
    var currentNode = findNode(event);
    if (currentNode != null) {
      LOG.debug("Start event id collision found:" + event.getId() + ", was also in node: " + currentNode.getTitle());
      return;
    }

    runUpdateAction(event, updatedNodes -> {
      if (event instanceof StartBuildEvent) {
        var node = getBuildProgressRootNode();
        addNode(event, node, updatedNodes);
        node.setTitle(myBuildDescriptor.getTitle());
        installContextMenu();
      }
      else {
        var parentNode = findParentNode(event);
        var node = new ExecutionNode(myProject, parentNode, false, this::isCorrectThread);
        addNode(event, node, updatedNodes);
      }
    });
  }

  private void onFinishEvent(@NotNull FinishEvent event) {
    var node = findNode(event);
    if (node == null) {
      LOG.debug("Finish event id collision found: the start event with " + event.getId() + " never handled.");
      return;
    }

    var result = calculateFinishResult(event, node);
    var firstFailureNode = new ExecutionNode[1];
    runUpdateAction(event, updatedNodes -> {
      setResult(node, result, updatedNodes);
      setAllDescendantResults(node, new SkippedResultImpl(), updatedNodes);
      setEndTime(node, event.getEventTime(), updatedNodes);

      if (result instanceof FailureResult failureResult) {
        for (var failure : failureResult.getFailures()) {
          var failureNode = addChildFailureNode(node, failure, event.getMessage(), event.getEventTime(), updatedNodes);
          if (firstFailureNode[0] == null) {
            firstFailureNode[0] = failureNode;
          }
        }
      }
    });

    if (firstFailureNode[0] != null) {
      showErrorIfFirst(firstFailureNode[0]);
    }

    if (event instanceof FinishBuildEvent) {
      myFinishedBuildEventReceived.set(true);
      myDeferredEvents.forEach(buildEvent -> onEventInternal(buildEvent));
      if (myConsoleViewHandler.getExecutionNode() == null) {
        invokeLater(() -> myConsoleViewHandler.setExecutionNode(getBuildProgressRootNode()));
      }
      myConsoleViewHandler.stopProgressBar();
    }
  }

  private void onProgressEvent(@NotNull ProgressBuildEvent event) {
    var existingNode = findNode(event);
    if (existingNode == null) {
      runUpdateAction(event, updatedNodes -> {
        var parentNode = findParentNode(event);
        var node = new ExecutionNode(myProject, parentNode, isBuildProgressRootNode(parentNode), this::isCorrectThread);
        addNode(event, node, updatedNodes);
      });
    }
    else if (isBuildProgressRootNode(existingNode)) {
      myConsoleViewHandler.updateProgressBar(event.getTotal(), event.getProgress());
    }
  }

  private void onMessageEvent(@NotNull MessageEvent event) {
    var existingNode = findNode(event);
    if (existingNode != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Message event id collision found:" + event.getId() + ", was also in node: " + existingNode.getTitle());
      }
      return;
    }

    runUpdateAction(event, updatedNodes -> {
      if (event instanceof DuplicateMessageAware) {
        if (!myFinishedBuildEventReceived.get()) {
          myDeferredEvents.add(event);
          return;
        }
      }

      var parentNode = findParentNode(event);
      if (event instanceof FileMessageEvent fileMessageEvent) {
        parentNode = getOrCreateFileMessageParentNode(
          fileMessageEvent.getEventTime(),
          fileMessageEvent.getFilePosition(),
          fileMessageEvent.getNavigatable(myProject),
          parentNode,
          updatedNodes
        );
      }

      if (event instanceof DuplicateMessageAware) {
        if (parentNode != null && parentNode.findFirstChild(node -> event.getMessage().equals(node.getName())) != null) {
          return;
        }
      }

      var node = new ExecutionNode(myProject, parentNode, false, this::isCorrectThread);
      addNode(event, node, updatedNodes);

      node.setAlwaysLeaf(event instanceof FileMessageEvent);
      node.setNavigatable(event.getNavigatable(myProject));
      setResult(node, event.getResult(), updatedNodes);
      setEndTime(node, event.getEventTime(), updatedNodes);

      if (parentNode != null && !isBuildProgressRootNode(parentNode)) {
        myConsoleViewHandler.withConsoleView(parentNode, consoleView ->
          consoleView.onEvent(event)
        );
      }
      myConsoleViewHandler.withConsoleView(node, consoleView ->
        consoleView.onEvent(event)
      );
    });

    var node = findNode(event);
    if (node != null) {
      if (event.getKind() == MessageEvent.Kind.ERROR) {
        showErrorIfFirst(node);
      }
      else {
        expandFirstMessage(node);
      }
    }
  }

  private void onPresentableEvent(@NotNull PresentableBuildEvent event) {
    var existingNode = findNode(event);
    if (existingNode != null) {
      LOG.debug("Presentable event id collision found:" + event.getId() + ", was also in node: " + existingNode.getTitle());
      return;
    }
    var parentNode = findParentNode(event);

    runUpdateAction(event, updatedNodes -> {
      var node = new ExecutionNode(myProject, parentNode, isBuildProgressRootNode(parentNode), this::isCorrectThread);
      addNode(event, node, updatedNodes);

      var presentationData = event.getPresentationData();
      node.applyFrom(presentationData);
      myConsoleViewHandler.maybeAddExecutionConsole(node, presentationData);
    });
  }

  private void onOutputEvent(@NotNull OutputBuildEvent event) {
    var existingNode = findNode(event);
    if (existingNode != null) {
      LOG.debug("Output event id collision found:" + event.getId() + ", was also in node: " + existingNode.getTitle());
      return;
    }
    var parentNode = getParentNode(event);

    myConsoleViewHandler.withConsoleView(parentNode, consoleView ->
      consoleView.onEvent(event)
    );
  }

  private void onBuildEvent(@NotNull BuildEvent event) {
    var existingNode = findNode(event);
    if (existingNode != null) {
      LOG.debug("Build event id collision found:" + event.getId() + ", was also in node: " + existingNode.getTitle());
      return;
    }
    var parentNode = getParentNode(event);

    runUpdateAction(event, __ -> {});

    myConsoleViewHandler.withConsoleView(parentNode, consoleView ->
      consoleView.onEvent(event)
    );
  }

  private static void setDefaultData(
    @NotNull BuildEvent event,
    @NotNull ExecutionNode node
  ) {
    node.setName(event.getMessage());
    if (node.getHint() == null) {
      node.setHint(event.getHint());
    }
    if (node.getStartTime() == 0) {
      node.setStartTime(event.getEventTime());
    }
  }

  private void setResult(
    @NotNull ExecutionNode node,
    @NotNull EventResult result,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    ContainerUtil.addIfNotNull(updatedNodes, node.setResult(result, !mySplitImplementation));

    var parentNode = node.getParent();
    var eventKind = result instanceof MessageEventResult messageEventResult ? messageEventResult.getKind() :
                    result instanceof FailureResult ? MessageEvent.Kind.ERROR :
                    null;
    if (eventKind != null && parentNode != null) {
      reportMessageKind(eventKind, parentNode, updatedNodes);
    }
  }

  private void setEndTime(
    @NotNull ExecutionNode node,
    @NotNull Long endTime,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    ContainerUtil.addIfNotNull(updatedNodes, node.setEndTime(endTime, !mySplitImplementation));
  }

  private void addNode(
    @NotNull BuildEvent event,
    @NotNull ExecutionNode node,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    addNode(event.getId(), node, updatedNodes);
  }

  private void addNode(
    @NotNull Object eventId,
    @NotNull ExecutionNode node,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    nodesMap.put(eventId, node);

    if (!mySplitImplementation) {
      var parentNode = node.getParent();
      if (parentNode != null) {
        updatedNodes.add(parentNode);
      }
    }
  }

  private @Nullable ExecutionNode findNode(@NotNull BuildEvent event) {
    return nodesMap.get(event.getId());
  }

  private @Nullable ExecutionNode findParentNode(@NotNull BuildEvent event) {
    return ObjectUtils.doIfNotNull(event.getParentId(), it -> nodesMap.get(it));
  }

  private @NotNull ExecutionNode getParentNode(@NotNull BuildEvent event) {
    return ObjectUtils.notNull(findParentNode(event), getBuildProgressRootNode());
  }

  private boolean isBuildProgressRootNode(@Nullable ExecutionNode node) {
    return node == getBuildProgressRootNode();
  }

  @TestOnly
  @ApiStatus.Internal
  public @NotNull ExecutionConsole getSelectedNodeConsole() {
    return myConsoleViewHandler.getCurrentConsoleOrEmpty();
  }

  private static @NotNull EventResult calculateFinishResult(
    @NotNull FinishEvent event,
    @NotNull ExecutionNode node
  ) {
    var result = event.getResult();
    if (result instanceof DerivedResult derivedResult) {
      if (node.getResult() != null) {
        // if another thread set result for child
        return node.getResult();
      }
      if (node.isFailed()) {
        return derivedResult.createFailureResult();
      }
      return derivedResult.createDefaultResult();
    }
    return result;
  }

  private void reportMessageKind(
    @NotNull MessageEvent.Kind eventKind,
    @NotNull ExecutionNode parentNode,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    if (eventKind == MessageEvent.Kind.ERROR || eventKind == MessageEvent.Kind.WARNING || eventKind == MessageEvent.Kind.INFO) {
      ExecutionNode rootNode = getRootElement();
      ExecutionNode executionNode = parentNode;
      do {
        ExecutionNode updatedRoot = executionNode.reportChildMessageKind(eventKind);
        if (mySplitImplementation) {
          if (executionNode != rootNode) {
            updatedNodes.add(executionNode);
          }
        }
        else {
          if (updatedRoot != null) {
            updatedNodes.add(updatedRoot);
          }
          else {
            scheduleUpdate(executionNode, false);
          }
        }
      }
      while ((executionNode = executionNode.getParent()) != null);
      scheduleUpdate(getRootElement(), false);
    }
  }

  private void expandFirstMessage(@NotNull ExecutionNode node) {
    if (!myExpandedFirstMessage.compareAndSet(false, true)) return;

    if (mySplitImplementation) {
      myTreeVm.makeVisible(node, false);
    }
    else {
      myTreeModel.invalidate(getRootElement(), false)
        .onProcessed(p -> {
          TreeUtil.promiseMakeVisible(myTree, visitor(node));
        });
    }
  }

  private void showErrorIfFirst(@NotNull ExecutionNode node) {
    if (!myShownFirstError.compareAndSet(false, true)) return;
    myExpandedFirstMessage.set(true);

    Runnable selectErrorNodeTask = () -> {
      if (mySplitImplementation) {
        myTreeVm.makeVisible(node, true);
      }
      else {
        TreeUtil.promiseSelect(myTree, visitor(node));
      }
      if (myNavigateToTheFirstErrorLocation) {
        var navigatable = node.getNavigatable();
        if (navigatable != null) {
          ApplicationManager.getApplication()
            .invokeLater(() -> navigatable.navigate(true), ModalityState.defaultModalityState(), myProject.getDisposed());
        }
      }
    };

    if (mySplitImplementation) {
      selectErrorNodeTask.run();
    }
    else {
      myTreeModel.invalidate(getRootElement(), true)
        .onProcessed(p -> selectErrorNodeTask.run());
    }
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurrenceNavigatorSupport.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurrenceNavigatorSupport.hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return myOccurrenceNavigatorSupport.goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myOccurrenceNavigatorSupport.goPreviousOccurence();
  }

  @Override
  public @NotNull String getNextOccurenceActionName() {
    return myOccurrenceNavigatorSupport.getNextOccurenceActionName();
  }

  @Override
  public @NotNull String getPreviousOccurenceActionName() {
    return myOccurrenceNavigatorSupport.getPreviousOccurenceActionName();
  }

  private static @NotNull TreeVisitor visitor(@NotNull ExecutionNode executionNode) {
    TreePath treePath = TreePathUtil.pathToCustomNode(executionNode, node -> node.getParent());
    return new TreeVisitor.ByTreePath<>(treePath, o -> (ExecutionNode)TreeUtil.getUserObject(o));
  }

  private @NotNull ExecutionNode addChildFailureNode(
    @NotNull ExecutionNode parentNode,
    @NotNull Failure failure,
    @NotNull String defaultFailureMessage,
    long eventTime,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    String message = ObjectUtils.chooseNotNull(failure.getMessage(), failure.getDescription());
    if (message == null && failure.getError() != null) {
      message = failure.getError().getMessage();
    }
    if (message == null) {
      message = defaultFailureMessage;
    }
    String failureNodeName = BuildConsoleUtils.getMessageTitle(message);
    Navigatable failureNavigatable = failure.getNavigatable();
    FilePosition filePosition = null;
    if (failureNavigatable instanceof FileNavigatable fileNavigatable) {
      filePosition = fileNavigatable.getFilePosition();
    }
    else if (failureNavigatable instanceof OpenFileDescriptor fileDescriptor) {
      Path path = VirtualFileUtil.toNioPathOrNull(fileDescriptor.getFile());
      if (path != null) {
        filePosition = new FilePosition(path, fileDescriptor.getLine(), fileDescriptor.getColumn());
      }
    }
    if (filePosition != null) {
      parentNode = getOrCreateFileMessageParentNode(eventTime, filePosition, failureNavigatable, parentNode, updatedNodes);
    }

    ExecutionNode failureNode = parentNode.findFirstChild(executionNode -> failureNodeName.equals(executionNode.getName()));
    if (failureNode == null) {
      failureNode = new ExecutionNode(myProject, parentNode, true, this::isCorrectThread);
      failureNode.setName(failureNodeName);
      if (filePosition != null && filePosition.getStartLine() >= 0) {
        failureNode.setHint(":" + (filePosition.getStartLine() + 1));
      }
    }
    failureNode.setNavigatable(failureNavigatable);

    List<Failure> failures = failureNode.getResult() instanceof FailureResult failureResult ?
                             ContainerUtil.append(failureResult.getFailures(), failure) :
                             Collections.singletonList(failure);
    setResult(failureNode, new FailureResultImpl(failures), updatedNodes);

    updatedNodes.add(failureNode);

    myConsoleViewHandler.withConsoleView(failureNode, consoleView ->
      consoleView.onFailure(failure)
    );
    return failureNode;
  }

  private void setAllDescendantResults(
    @NotNull ExecutionNode node, @NotNull EventResult result, @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    List<ExecutionNode> childList = node.getChildList();
    if (childList.isEmpty()) return;
    // Make a copy of the list since child.setResult may remove items from the collection.
    for (ExecutionNode child : new ArrayList<>(childList)) {
      if (!child.isRunning()) {
        continue;
      }
      setAllDescendantResults(child, result, updatedNodes);
      setResult(child, result, updatedNodes);
      if (mySplitImplementation) {
        updatedNodes.add(child);
      }
    }
  }

  @Override
  public void scrollTo(int offset) {
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public void setOutputPaused(boolean value) {
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {
  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @Override
  public AnAction @NotNull [] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public void allowHeavyFilters() {
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    if (!mySplitImplementation) {
      return myTree;
    }

    if (mySplitComponent instanceof ComponentContainer splitComponentContainer) {
      return splitComponentContainer.getPreferredFocusableComponent();
    }

    return mySplitComponent;
  }

  @Override
  public void dispose() {
    myDisposed.set(true);
    if (mySplitImplementation) {
      kotlinx.coroutines.CoroutineScopeKt.cancel(myScope, null);
    }
  }

  public boolean isDisposed() {
    return myDisposed.get();
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    myInvoker.invoke(() -> onEventInternal(event));
  }

  private void runUpdateAction(@NotNull BuildEvent event, @NotNull Consumer<Set<ExecutionNode>> action) {
    var updatedNodes = mySplitImplementation ? new LinkedHashSet<ExecutionNode>() : new SmartHashSet<ExecutionNode>();

    action.accept(updatedNodes);

    var node = findNode(event);
    if (node == null) {
      return;
    }

    setDefaultData(event, node);

    var parentStructureChanged = !updatedNodes.isEmpty();
    updatedNodes.add(node);
    if (mySplitImplementation) {
      myTreeVm.createOrUpdateNodes(updatedNodes);
    }
    else {
      for (var updatedNode : updatedNodes) {
        scheduleUpdate(updatedNode, parentStructureChanged);
      }
    }
  }

  private void scheduleUpdate(ExecutionNode executionNode, boolean parentStructureChanged) {
    if (mySplitImplementation) return;
    ExecutionNode node = (executionNode.getParent() == null || !parentStructureChanged) ? executionNode : executionNode.getParent();
    myTreeModel.invalidate(node, parentStructureChanged);
  }

  @Contract("_, _, _, null, _ -> null; _, _, _, !null, _ -> !null")
  private @Nullable ExecutionNode getOrCreateFileMessageParentNode(
    long eventTime,
    @NotNull FilePosition filePosition,
    @Nullable Navigatable navigatable,
    @Nullable ExecutionNode parentNode,
    @NotNull Set<? super ExecutionNode> updatedNodes
  ) {
    var filePositionPath = filePosition.getPath();
    if (filePositionPath == null) {
      return parentNode;
    }
    var filePath = NioPathUtil.toCanonicalPath(filePositionPath);
    var existingNode = nodesMap.get(filePath);
    if (existingNode != null) {
      return existingNode;
    }
    var node = createFileMessageParentNode(eventTime, filePositionPath, navigatable, parentNode);
    if (node == null) {
      return parentNode;
    }
    addNode(filePath, node, updatedNodes);
    return node;
  }

  private @Nullable ExecutionNode createFileMessageParentNode(
    long eventTime,
    @NotNull Path filePositionPath,
    @Nullable Navigatable navigatable,
    @Nullable ExecutionNode parentNode
  ) {
    var filePath = NioPathUtil.toCanonicalPath(filePositionPath);

    var parentsPath = "";
    var relativePath = FileUtilRt.getRelativePath(myWorkingDir, filePath, '/');
    if (relativePath != null) {
      if (relativePath.equals(".")) {
        return null;
      }
      if (!relativePath.startsWith("../../")) {
        parentsPath = myWorkingDir;
      }
    }
    if (parentsPath.isEmpty()) {
      if (FileUtil.isAncestor(SystemProperties.getUserHome(), filePath, true)) {
        relativePath = FileUtil.getLocationRelativeToUserHome(filePath, false);
      }
      else {
        relativePath = filePath;
      }
    }
    else {
      relativePath = getRelativePath(parentsPath, filePath);
    }

    var path = Path.of(relativePath);
    var node = new ExecutionNode(myProject, parentNode, false, this::isCorrectThread);
    node.setName(path.getFileName().toString());
    var pathParent = path.getParent();
    if (pathParent != null) {
      node.setHint(pathParent.toString());
    }
    node.setStartTime(eventTime);
    node.setEndTime(eventTime);
    node.setIconProvider(() -> ObjectUtils.doIfNotNull(
      VfsUtil.findFile(filePositionPath, false), it -> it.getFileType().getIcon()
    ));
    node.setNavigatable(navigatable);
    return node;
  }

  private static String getRelativePath(@NotNull String basePath, @NotNull String filePath) {
    String path = ObjectUtils.notNull(FileUtil.getRelativePath(basePath, filePath, '/'), filePath);
    if (path.startsWith("..") && FileUtil.isAncestor(SystemProperties.getUserHome(), filePath, true)) {
      return FileUtil.getLocationRelativeToUserHome(filePath, false);
    }
    return path;
  }

  public void hideRootNode() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myTree != null) {
        myTree.setRootVisible(false);
        myTree.setShowsRootHandles(true);
      }
    });
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, "reference.build.tool.window");
    sink.set(CommonDataKeys.PROJECT, myProject);
    if (mySplitImplementation) {
      sink.set(COMPONENT_KEY, this);
    }
    else {
      sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, extractSelectedNodesNavigatables());
      sink.set(CommonDataKeys.NAVIGATABLE, extractSelectedNodeNavigatable());
    }
  }

  private @Nullable Navigatable extractSelectedNodeNavigatable() {
    TreePath selectedPath = TreeUtil.getSelectedPathIfOne(myTree);
    if (selectedPath == null) return null;
    DefaultMutableTreeNode node = ObjectUtils.tryCast(selectedPath.getLastPathComponent(), DefaultMutableTreeNode.class);
    if (node == null) return null;
    ExecutionNode executionNode = ObjectUtils.tryCast(node.getUserObject(), ExecutionNode.class);
    if (executionNode == null) return null;
    return executionNode.getNavigatable();
  }

  private Navigatable @Nullable [] extractSelectedNodesNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (ExecutionNode each : getSelectedNodes()) {
      navigatables.addAll(each.getNavigatables());
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
  }

  private ExecutionNode[] getSelectedNodes() {
    final ExecutionNode[] result = new ExecutionNode[0];
    if (myTree != null) {
      final List<ExecutionNode> nodes =
        TreeUtil.collectSelectedObjects(myTree, path -> TreeUtil.getLastUserObject(ExecutionNode.class, path));
      return nodes.toArray(result);
    }
    return result;
  }

  @ApiStatus.Internal
  public JTree getTree() {
    if (mySplitImplementation) {
      // won't work on rem dev backend
      return UIUtil.findComponentOfType(mySplitComponent, JTree.class);
    }
    else {
      return myTree;
    }
  }

  @ApiStatus.Internal
  public void clearTreeSelection() {
    if (mySplitImplementation) {
      myTreeVm.clearSelection();
    }
    else {
      myTree.clearSelection();
    }
  }

  private static Tree initTree(@NotNull AsyncTreeModel model) {
    Tree tree = new Tree(model);
    tree.setLargeModel(true);
    ComponentUtil.putClientProperty(tree, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);
    ComponentUtil.putClientProperty(tree, DefaultTreeUI.AUTO_EXPAND_ALLOWED, false);
    tree.setRootVisible(false);
    EditSourceOnDoubleClickHandler.install(tree);
    EditSourceOnEnterKeyHandler.install(tree);
    TreeSpeedSearch.installOn(tree).setComparator(new SpeedSearchComparator(false));
    TreeUtil.installActions(tree);
    if (Registry.is("build.toolwindow.show.inline.statistics")) {
      tree.setCellRenderer(new MyNodeRenderer());
    }
    tree.putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true);
    return tree;
  }

  @ApiStatus.Internal
  public Promise<?> invokeLater(@NotNull Runnable task) {
    return myInvoker.invokeLater(task);
  }

  void selectNode(@NotNull ExecutionNode node) {
    myConsoleViewHandler.setNodeIfChanged(node);
  }

  BuildViewId getBuildViewId() {
    return mySplitImplementation ? myTreeVm.getId() : null;
  }

  @NotNull
  JComponent getConsoleComponent() {
    return myConsoleViewHandler.getComponent();
  }

  private static final class ProblemOccurrenceNavigatorSupport extends OccurenceNavigatorSupport {
    ProblemOccurrenceNavigatorSupport(final Tree tree) {
      super(tree);
    }

    @Override
    protected Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ExecutionNode executionNode)) {
        return null;
      }
      if (node.getChildCount() != 0 || !executionNode.hasWarnings() && !executionNode.isFailed()) {
        return null;
      }
      return executionNode.getNavigatable();
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.problem");
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.problem");
    }
  }

  private static final class SplitProblemOccurrenceNavigatorSupport implements OccurenceNavigator {
    private final BuildTreeViewModel vm;

    private SplitProblemOccurrenceNavigatorSupport(BuildTreeViewModel model) {
      vm = model;
    }

    @Override
    public boolean hasNextOccurence() {
      return vm.canNavigate(true);
    }

    @Override
    public boolean hasPreviousOccurence() {
      return vm.canNavigate(false);
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      vm.navigate(true);
      return null;
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
      vm.navigate(false);
      return null;
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.problem");
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.problem");
    }
  }

  private static final class MyNodeRenderer extends NodeRenderer {
    private String myDurationText;
    private Color myDurationColor;
    private int myDurationWidth;
    private int myDurationLeftInset;
    private int myDurationRightInset;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      myDurationText = null;
      myDurationColor = null;
      myDurationWidth = 0;
      myDurationLeftInset = 0;
      myDurationRightInset = 0;
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      final Object userObj = node.getUserObject();
      if (userObj instanceof ExecutionNode) {
        myDurationText = ((ExecutionNode)userObj).getDuration();
        if (myDurationText != null) {
          FontMetrics metrics = getFontMetrics(RelativeFont.SMALL.derive(getFont()));
          myDurationWidth = metrics.stringWidth(myDurationText);
          myDurationLeftInset = metrics.getHeight() / 4;
          myDurationRightInset = ExperimentalUI.isNewUI() ? tree.getInsets().right + JBUI.scale(4) : myDurationLeftInset;
          myDurationColor = selected ? UIUtil.getTreeSelectionForeground(hasFocus) : SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
        }
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      UISettings.setupAntialiasing(g);
      Shape clip = null;
      int width = getWidth();
      int height = getHeight();
      if (isOpaque()) {
        // paint background for expanded row
        g.setColor(getBackground());
        g.fillRect(0, 0, width, height);
      }
      if (myDurationWidth > 0) {
        width -= myDurationWidth + myDurationLeftInset + myDurationRightInset;
        if (width > 0 && height > 0) {
          g.setColor(myDurationColor);
          g.setFont(RelativeFont.SMALL.derive(getFont()));
          g.drawString(myDurationText, width + myDurationLeftInset, getTextBaseLine(g.getFontMetrics(), height));
          clip = g.getClip();
          g.clipRect(0, 0, width, height);
        }
      }

      super.paintComponent(g);
      // restore clip area if needed
      if (clip != null) g.setClip(clip);
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      if (myDurationWidth > 0) {
        preferredSize.width += myDurationWidth + myDurationLeftInset + myDurationRightInset;
      }
      return preferredSize;
    }
  }

  @ApiStatus.Internal
  public final class MyTreeStructure extends AbstractTreeStructure {
    @Override
    public @NotNull Object getRootElement() {
      return BuildTreeConsoleView.this.getRootElement();
    }

    @Override
    public Object @NotNull [] getChildElements(@NotNull Object element) {
      // This .toArray() is still slow, but it is called less frequently because of batching in AsyncTreeModel and process less data if
      // filters are applied.
      return ((ExecutionNode)element).getChildList().toArray();
    }

    @Override
    public @Nullable Object getParentElement(@NotNull Object element) {
      return ((ExecutionNode)element).getParent();
    }

    @Override
    public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, @Nullable NodeDescriptor parentDescriptor) {
      return ((NodeDescriptor)element);
    }

    @Override
    public void commit() { }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(@NotNull Object element) {
      return ((ExecutionNode)element).isAlwaysLeaf();
    }
  }

  private final class ExecutionNodeAutoExpandingListener implements TreeModelListener {
    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      maybeExpand(e.getTreePath());
    }

    @Override
    public void treeNodesChanged(TreeModelEvent e) {
      // ExecutionNode should never change its isAutoExpand state. Ignore calls and do nothing.
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
      // A removed node is not a reason to expand it parent. Ignore calls and do nothing.
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
      // We do not expect this event to happen in cases other than clearing the tree (including changing the filter).
      // Ignore calls and do nothing.
    }

    private boolean maybeExpand(TreePath path) {
      if (myTree == null || path == null) return false;
      Object last = path.getLastPathComponent();
      if (last instanceof DefaultMutableTreeNode mutableTreeNode) {
        boolean expanded = false;
        Enumeration<?> children = mutableTreeNode.children();
        if (children.hasMoreElements()) {
          while (children.hasMoreElements()) {
            Object next = children.nextElement();
            if (next != null) {
              expanded = maybeExpand(path.pathByAddingChild(next)) || expanded;
            }
          }
          if (expanded) return true;
          Object lastUserObject = mutableTreeNode.getUserObject();
          if (lastUserObject instanceof ExecutionNode) {
            if (((ExecutionNode)lastUserObject).isAutoExpandNode()) {
              if (!myTree.isExpanded(path)) {
                myTree.expandPath(path);
                return true;
              }
            }
          }
        }
      }
      return false;
    }
  }
}