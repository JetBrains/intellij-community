// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
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
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.ui.render.RenderingHelper;
import com.intellij.ui.split.SplitComponentFactory;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.*;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public final class BuildTreeConsoleView implements ConsoleView, UiDataProvider, BuildConsoleView, Filterable<ExecutionNode>, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance(BuildTreeConsoleView.class);
  @ApiStatus.Internal
  public static final DataKey<BuildTreeConsoleView> COMPONENT_KEY = DataKey.create("BuildTreeConsoleView");

  private static final @NonNls String TREE = "tree";
  private static final @NonNls String SPLITTER_PROPERTY = "BuildView.Splitter.Proportion";

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
  private final ConsoleViewHandler myConsoleViewHandler;
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
  private final ComponentContainer mySplitComponent;
  private final ExecutionNode myRootNode;
  private final ExecutionNode myBuildProgressRootNode;
  private final Set<Predicate<? super ExecutionNode>> myNodeFilters;
  private final OccurenceNavigator myOccurrenceNavigatorSupport;
  private final Set<BuildEvent> myDeferredEvents = ConcurrentCollectionFactory.createConcurrentSet();

  private final boolean mySplitImplementation = Registry.is("build.toolwindow.split.tree", false);

  /**
   * @deprecated BuildViewSettingsProvider is not used anymore.
   */
  @Deprecated(forRemoval = true)
  public BuildTreeConsoleView(@NotNull Project project,
                              @NotNull BuildDescriptor buildDescriptor,
                              @Nullable ExecutionConsole executionConsole,
                              @NotNull BuildViewSettingsProvider buildViewSettingsProvider) {
    this(project, buildDescriptor, executionConsole);
  }

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
      Disposer.register(this, myTreeVm);
      mySplitComponent = SplitComponentFactory.getInstance().createComponent(myTreeVm);

      treeComponent = mySplitComponent.getComponent();

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
    OnePixelSplitter myThreeComponentsSplitter = new OnePixelSplitter(SPLITTER_PROPERTY, 0.33f);
    myThreeComponentsSplitter.setFirstComponent(treeComponent);
    List<Filter> filters = myBuildDescriptor.getExecutionFilters();
    myConsoleViewHandler = new ConsoleViewHandler(myProject, myTree, myBuildProgressRootNode, this,
                                                  executionConsole, filters);
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
  private Function<@Nullable ExecutionNode, @NotNull ActionGroup> getContextMenuSupplier() {
    final DefaultActionGroup rerunActionGroup = new DefaultActionGroup();
    List<AnAction> restartActions = myBuildDescriptor.getRestartActions();
    rerunActionGroup.addAll(restartActions);
    if (!restartActions.isEmpty()) {
      rerunActionGroup.addSeparator();
    }

    final DefaultActionGroup sourceActionGroup = new DefaultActionGroup();
    EditSourceAction edit = new EditSourceAction();
    ActionUtil.copyFrom(edit, "EditSource");
    sourceActionGroup.add(edit);
    DefaultActionGroup filteringActionsGroup = BuildTreeFilters.createFilteringActionsGroup(new WeakFilterableSupplier<>(this));
    final DefaultActionGroup navigationActionGroup = new DefaultActionGroup();
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    final AnAction prevAction = actionsManager.createPrevOccurenceAction(this);
    navigationActionGroup.add(prevAction);
    final AnAction nextAction = actionsManager.createNextOccurenceAction(this);
    navigationActionGroup.add(nextAction);

    return node -> {
      final DefaultActionGroup group = new DefaultActionGroup();
      group.addAll(rerunActionGroup);
      group.addAll(sourceActionGroup);
      group.addSeparator();
      if (node != null) {
        List<AnAction> contextActions = myBuildDescriptor.getContextActions(node);
        if (!contextActions.isEmpty()) {
          group.addAll(contextActions);
          group.addSeparator();
        }
      }
      group.addAll(filteringActionsGroup);
      group.addSeparator();
      group.addAll(navigationActionGroup);
      return group;
    };
  }

  private void installContextMenu() {
    if (myTree == null) return;
    UIUtil.invokeLaterIfNeeded(() -> {
      Function<ExecutionNode, ActionGroup> supplier = getContextMenuSupplier();
      myTree.addMouseListener(new PopupHandler() {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          ExecutionNode[] selectedNodes = getSelectedNodes();
          ExecutionNode selectedNode = selectedNodes.length == 1 ? selectedNodes[0] : null;
          ActionGroup group = supplier.apply(selectedNode);
          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("BuildView", group);
          popupMenu.setTargetComponent(myTree);
          JPopupMenu menu = popupMenu.getComponent();
          menu.show(comp, x, y);
        }
      });
    });
  }

  @ApiStatus.Internal
  public ActionGroup getContextMenuGroup(DataContext context) {
    SelectedBuildTreeNode selectedNode = context.getData(BuildTreeContextKt.getBUILD_TREE_SELECTED_NODE());
    ExecutionNode node = selectedNode == null ? null : myTreeVm.getNodeById(selectedNode.getNodeId());
    return getContextMenuSupplier().apply(node);
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

  boolean isAlwaysVisible(ExecutionNode node) {
    return node == getBuildProgressRootNode() || node.isRunning() || node.isFailed();
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

  ExecutionNode getRootElement() {
    return myRootNode;
  }

  ExecutionNode getBuildProgressRootNode() {
    return myBuildProgressRootNode;
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
  }

  private @Nullable ExecutionNode getOrMaybeCreateParentNode(@NotNull BuildEvent event,
                                                             @NotNull Set<? super ExecutionNode> updatedNodes) {
    ExecutionNode parentNode = event.getParentId() == null ? null : nodesMap.get(event.getParentId());
    if (event instanceof MessageEvent) {
      parentNode = createMessageParentNodes((MessageEvent)event, parentNode, updatedNodes);
      if (!mySplitImplementation) {
        ContainerUtil.addIfNotNull(updatedNodes, parentNode);
      }
    }
    return parentNode;
  }

  private void onEventInternal(@NotNull Object buildId, @NotNull BuildEvent event) {
    Set<ExecutionNode> updatedNodes = mySplitImplementation ? new LinkedHashSet<>() : new SmartHashSet<>();
    final ExecutionNode parentNode = getOrMaybeCreateParentNode(event, updatedNodes);
    final Object eventId = event.getId();
    ExecutionNode currentNode = nodesMap.get(eventId);
    ExecutionNode buildProgressRootNode = getBuildProgressRootNode();
    Runnable selectErrorNodeTask = null;
    boolean isMessageEvent = event instanceof MessageEvent;
    if (event instanceof StartEvent || isMessageEvent) {
      if (currentNode == null) {
        if (event instanceof DuplicateMessageAware) {
          if (myFinishedBuildEventReceived.get()) {
            if (parentNode != null &&
                parentNode.findFirstChild(node -> event.getMessage().equals(node.getName())) != null) {
              if (mySplitImplementation) {
                myTreeVm.createOrUpdateNodes(updatedNodes);
              }
              return;
            }
          }
          else {
            myDeferredEvents.add(event);
            if (mySplitImplementation) {
              myTreeVm.createOrUpdateNodes(updatedNodes);
            }
            return;
          }
        }
        if (event instanceof StartBuildEvent) {
          currentNode = buildProgressRootNode;
          installContextMenu();
          currentNode.setTitle(myBuildDescriptor.getTitle());
        }
        else {
          currentNode = new ExecutionNode(myProject, parentNode, false, this::isCorrectThread);

          if (isMessageEvent) {
            currentNode.setAlwaysLeaf(event instanceof FileMessageEvent);
            MessageEvent messageEvent = (MessageEvent)event;
            currentNode.setStartTime(messageEvent.getEventTime());
            currentNode.setEndTime(messageEvent.getEventTime(), false);
            Navigatable messageEventNavigatable = messageEvent.getNavigatable(myProject);
            currentNode.setNavigatable(messageEventNavigatable);
            MessageEventResult messageEventResult = messageEvent.getResult();
            ContainerUtil.addIfNotNull(updatedNodes, currentNode.setResult(messageEventResult, !mySplitImplementation));

            if (messageEventResult instanceof FailureResult) {
              for (Failure failure : ((FailureResult)messageEventResult).getFailures()) {
                selectErrorNodeTask =
                  selectErrorNodeTask != null ? selectErrorNodeTask : showErrorIfFirst(currentNode, failure.getNavigatable());
              }
            }
            if (messageEvent.getKind() == MessageEvent.Kind.ERROR) {
              selectErrorNodeTask =
                selectErrorNodeTask != null ? selectErrorNodeTask : showErrorIfFirst(currentNode, messageEventNavigatable);
            }

            if (parentNode != null) {
              if (parentNode != buildProgressRootNode) {
                myConsoleViewHandler.addOutput(parentNode, buildId, event);
                myConsoleViewHandler.addOutput(parentNode);
              }
              reportMessageKind(messageEvent.getKind(), parentNode, updatedNodes);
            }
            myConsoleViewHandler.addOutput(currentNode, buildId, event);
          }
          if (parentNode != null) {
            if (!mySplitImplementation) {
              updatedNodes.add(parentNode);
            }
            parentNode.add(currentNode);
          }
        }
        nodesMap.put(eventId, currentNode);
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("start event id collision found:" + eventId + ", was also in node: " + currentNode.getTitle());
        }
        if (mySplitImplementation) {
          myTreeVm.createOrUpdateNodes(updatedNodes);
        }
        return;
      }
    }
    else {
      boolean isProgress = event instanceof ProgressBuildEvent;
      currentNode = nodesMap.get(eventId);
      if (currentNode == null) {
        if (isProgress) {
          currentNode = new ExecutionNode(myProject, parentNode, parentNode == buildProgressRootNode, this::isCorrectThread);
          nodesMap.put(eventId, currentNode);
          if (parentNode != null) {
            if (!mySplitImplementation) {
              updatedNodes.add(parentNode);
            }
            parentNode.add(currentNode);
          }
        }
        else if (event instanceof OutputBuildEvent && parentNode != null) {
          myConsoleViewHandler.addOutput(parentNode, buildId, event);
        }
        else if (event instanceof PresentableBuildEvent) {
          currentNode = addAsPresentableEventNode((PresentableBuildEvent)event, updatedNodes, parentNode, eventId, buildProgressRootNode);
        }
      }

      if (isProgress) {
        ProgressBuildEvent progressBuildEvent = (ProgressBuildEvent)event;
        long total = progressBuildEvent.getTotal();
        long progress = progressBuildEvent.getProgress();
        if (currentNode == myBuildProgressRootNode) {
          myConsoleViewHandler.updateProgressBar(total, progress);
        }
      }
    }

    if (currentNode == null) {
      if (mySplitImplementation) {
        myTreeVm.createOrUpdateNodes(updatedNodes);
      }
      return;
    }

    if (mySplitImplementation) {
      updatedNodes.add(currentNode);
    }
    currentNode.setName(event.getMessage());
    currentNode.setHint(event.getHint());
    if (currentNode.getStartTime() == 0) {
      currentNode.setStartTime(event.getEventTime());
    }

    if (event instanceof FinishEvent) {
      EventResult result = ((FinishEvent)event).getResult();
      if (result instanceof DerivedResult) {
        result = calculateDerivedResult((DerivedResult)result, currentNode);
      }
      currentNode.setResult(result, false);
      ContainerUtil.addIfNotNull(updatedNodes, currentNode.setEndTime(event.getEventTime(), !mySplitImplementation));
      SkippedResult skippedResult = new SkippedResultImpl();
      finishChildren(updatedNodes, currentNode, skippedResult);
      if (result instanceof FailureResult) {
        for (Failure failure : ((FailureResult)result).getFailures()) {
          Runnable task = addChildFailureNode(currentNode, failure, event.getMessage(), event.getEventTime(), updatedNodes);
          if (selectErrorNodeTask == null) selectErrorNodeTask = task;
        }
      }
    }

    if (event instanceof FinishBuildEvent) {
      myFinishedBuildEventReceived.set(true);
      String aHint = event.getHint();
      String time = DateFormatUtil.formatDateTime(event.getEventTime());
      aHint = aHint == null ?
              LangBundle.message("build.event.message.at", time) :
              LangBundle.message("build.event.message.0.at.1", aHint, time);
      currentNode.setHint(aHint);
      myDeferredEvents.forEach(buildEvent -> onEventInternal(buildId, buildEvent));
      if (myConsoleViewHandler.myExecutionNode == null) {
        invokeLater(() -> myConsoleViewHandler.setNode(buildProgressRootNode));
      }
      myConsoleViewHandler.stopProgressBar();
    }
    if (mySplitImplementation) {
      myTreeVm.createOrUpdateNodes(updatedNodes);
    }
    else {
      if (updatedNodes.isEmpty()) {
        scheduleUpdate(currentNode, false);
      }
      else {
        for (ExecutionNode node : updatedNodes) {
          scheduleUpdate(node, true);
        }
      }
    }
    if (selectErrorNodeTask != null) {
      myExpandedFirstMessage.set(true);
      if (mySplitImplementation) {
        selectErrorNodeTask.run();
      }
      else {
        Runnable finalSelectErrorTask = selectErrorNodeTask;
        myTreeModel.invalidate(getRootElement(), true).onProcessed(p -> finalSelectErrorTask.run());
      }
    }
    else {
      if (isMessageEvent && myExpandedFirstMessage.compareAndSet(false, true)) {
        if (mySplitImplementation) {
          myTreeVm.makeVisible(currentNode, false);
        }
        else {
          ExecutionNode finalCurrentNode = currentNode;
          myTreeModel.invalidate(getRootElement(), false).onProcessed(p -> {
            TreeUtil.promiseMakeVisible(myTree, visitor(finalCurrentNode));
          });
        }
      }
    }
  }

  private @NotNull ExecutionNode addAsPresentableEventNode(@NotNull PresentableBuildEvent event,
                                                           @NotNull Set<? super ExecutionNode> updatedNodes,
                                                           @Nullable ExecutionNode parentNode,
                                                           @NotNull Object eventId,
                                                           @NotNull ExecutionNode buildProgressRootNode) {
    ExecutionNode executionNode = new ExecutionNode(myProject, parentNode, parentNode == buildProgressRootNode, this::isCorrectThread);
    BuildEventPresentationData presentationData = event.getPresentationData();
    executionNode.applyFrom(presentationData);
    nodesMap.put(eventId, executionNode);
    if (parentNode != null) {
      if (!mySplitImplementation) {
        updatedNodes.add(parentNode);
      }
      parentNode.add(executionNode);
    }
    myConsoleViewHandler.maybeAddExecutionConsole(executionNode, presentationData);
    return executionNode;
  }

  @ApiStatus.Internal
  @TestOnly
  public @Nullable ExecutionConsole getSelectedNodeConsole() {
    ExecutionConsole console = ObjectUtils.notNull(
      myConsoleViewHandler.getCurrentConsole(),
      () -> myConsoleViewHandler.getEmptyConsole()
    );
    if (console instanceof ConsoleViewImpl) {
      ((ConsoleViewImpl)console).flushDeferredText();
    }
    return console;
  }

  private static EventResult calculateDerivedResult(DerivedResult result, ExecutionNode node) {
    if (node.getResult() != null) {
      return node.getResult(); // if another thread set result for child
    }
    if (node.isFailed()) {
      return result.createFailureResult();
    }

    return result.createDefaultResult();
  }

  private void reportMessageKind(@NotNull MessageEvent.Kind eventKind,
                                 @NotNull ExecutionNode parentNode,
                                 @NotNull Set<? super ExecutionNode> updatedNodes) {
    if (eventKind == MessageEvent.Kind.ERROR || eventKind == MessageEvent.Kind.WARNING || eventKind == MessageEvent.Kind.INFO) {
      ExecutionNode executionNode = parentNode;
      do {
        ExecutionNode updatedRoot = executionNode.reportChildMessageKind(eventKind);
        if (mySplitImplementation) {
          if (executionNode != myRootNode) {
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

  private @Nullable Runnable showErrorIfFirst(@NotNull ExecutionNode node, @Nullable Navigatable navigatable) {
    if (myShownFirstError.compareAndSet(false, true)) {
      return () -> {
        if (mySplitImplementation) {
          myTreeVm.makeVisible(node, true);
        }
        else {
          TreeUtil.promiseSelect(myTree, visitor(node));
        }
        if (myNavigateToTheFirstErrorLocation && navigatable != null && navigatable != NonNavigatable.INSTANCE) {
          ApplicationManager.getApplication()
            .invokeLater(() -> navigatable.navigate(true), ModalityState.defaultModalityState(), myProject.getDisposed());
        }
      };
    }
    return null;
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

  private @Nullable Runnable addChildFailureNode(@NotNull ExecutionNode parentNode,
                                                 @NotNull Failure failure,
                                                 @NotNull String defaultFailureMessage,
                                                 long eventTime,
                                                 @NotNull Set<? super ExecutionNode> updatedNodes) {
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
    if (failureNavigatable instanceof OpenFileDescriptor fileDescriptor) {
      File file = VfsUtilCore.virtualToIoFile(fileDescriptor.getFile());
      filePosition = new FilePosition(file, fileDescriptor.getLine(), fileDescriptor.getColumn());
      parentNode = createMessageParentNodes(eventTime, filePosition, failureNavigatable, parentNode, updatedNodes);
    } else if (failureNavigatable instanceof FileNavigatable) {
      filePosition = ((FileNavigatable)failureNavigatable).getFilePosition();
      parentNode = createMessageParentNodes(eventTime, filePosition, failureNavigatable, parentNode, updatedNodes);
    }

    ExecutionNode failureNode = parentNode.findFirstChild(executionNode -> failureNodeName.equals(executionNode.getName()));
    if (failureNode == null) {
      failureNode = new ExecutionNode(myProject, parentNode, true, this::isCorrectThread);
      failureNode.setName(failureNodeName);
      if (filePosition != null && filePosition.getStartLine() >= 0) {
        String hint = ":" + (filePosition.getStartLine() + 1);
        failureNode.setHint(hint);
      }
      parentNode.add(failureNode);
      reportMessageKind(MessageEvent.Kind.ERROR, parentNode, updatedNodes);
    }
    if (failureNavigatable != null && failureNavigatable != NonNavigatable.INSTANCE) {
      failureNode.setNavigatable(failureNavigatable);
    }

    List<Failure> failures;
    EventResult result = failureNode.getResult();
    if (result instanceof FailureResult) {
      failures = new ArrayList<>(((FailureResult)result).getFailures());
      failures.add(failure);
    }
    else {
      failures = Collections.singletonList(failure);
    }
    ExecutionNode updatedRoot = failureNode.setResult(new FailureResultImpl(failures));
    if (mySplitImplementation) {
      updatedNodes.add(failureNode);
    }
    else {
      updatedNodes.add(ObjectUtils.notNull(updatedRoot, parentNode));
    }
    myConsoleViewHandler.addOutput(failureNode, failure);
    return showErrorIfFirst(failureNode, failureNavigatable);
  }

  private void finishChildren(@NotNull Set<? super ExecutionNode> updatedNodes,
                                     @NotNull ExecutionNode node,
                                     @NotNull EventResult result) {
    List<ExecutionNode> childList = node.getChildList();
    if (childList.isEmpty()) return;
    // Make a copy of the list since child.setResult may remove items from the collection.
    for (ExecutionNode child : new ArrayList<>(childList)) {
      if (!child.isRunning()) {
        continue;
      }
      finishChildren(updatedNodes, child, result);
      ContainerUtil.addIfNotNull(updatedNodes, child.setResult(result, !mySplitImplementation));
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
    return mySplitImplementation ? ObjectUtils.notNull(mySplitComponent.getPreferredFocusableComponent(), mySplitComponent.getComponent())
                                 : myTree;
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
    myInvoker.invoke(() -> onEventInternal(buildId, event));
  }

  void scheduleUpdate(ExecutionNode executionNode, boolean parentStructureChanged) {
    if (mySplitImplementation) return;
    ExecutionNode node = (executionNode.getParent() == null || !parentStructureChanged) ? executionNode : executionNode.getParent();
    myTreeModel.invalidate(node, parentStructureChanged);
  }

  private ExecutionNode createMessageParentNodes(MessageEvent messageEvent,
                                                 ExecutionNode parentNode,
                                                 @NotNull Set<? super ExecutionNode> updatedNodes) {
    Object messageEventParentId = messageEvent.getParentId();
    if (messageEventParentId == null) return null;
    if (messageEvent instanceof FileMessageEvent) {
      return createMessageParentNodes(messageEvent.getEventTime(),
                                      ((FileMessageEvent)messageEvent).getFilePosition(),
                                      messageEvent.getNavigatable(myProject),
                                      parentNode,
                                      updatedNodes);
    } else {
      return parentNode;
    }
  }

  private ExecutionNode createMessageParentNodes(long eventTime,
                                                 @NotNull FilePosition filePosition,
                                                 @Nullable Navigatable navigatable,
                                                 ExecutionNode parentNode,
                                                 @NotNull Set<? super ExecutionNode> updatedNodes) {
    File filePositionFile = filePosition.getFile();
    String filePath = filePositionFile == null ? "" : FileUtilRt.toSystemIndependentName(filePositionFile.getPath());
    String parentsPath = "";

    String relativePath = FileUtilRt.getRelativePath(myWorkingDir, filePath, '/');
    if (relativePath != null) {
      if (relativePath.equals(".")) {
        return parentNode;
      }
      if (!relativePath.startsWith("../../")) {
        parentsPath = myWorkingDir;
      }
    }

    if (parentsPath.isEmpty()) {
      File userHomeDir = new File(SystemProperties.getUserHome());
      if (FileUtil.isAncestor(userHomeDir, new File(filePath), true)) {
        relativePath = FileUtil.getLocationRelativeToUserHome(filePath, false);
      }
      else {
        relativePath = filePath;
      }
    }
    else {
      relativePath = getRelativePath(parentsPath, filePath);
    }
    Path path = Path.of(relativePath);
    String nodeName = path.getFileName().toString();
    Path pathParent = path.getParent();
    String pathHint = pathParent == null ? null : pathParent.toString();
    parentNode = getOrCreateMessagesNode(eventTime, filePath, parentNode, nodeName, pathHint, () -> {
      VirtualFile file = filePositionFile == null ? null : VfsUtil.findFileByIoFile(filePositionFile, false);
      if (file != null) {
        return file.getFileType().getIcon();
      }
      return null;
    }, navigatable, nodesMap, myProject, updatedNodes);
    return parentNode;
  }

  private static String getRelativePath(@NotNull String basePath, @NotNull String filePath) {
    String path = Objects.requireNonNullElse(FileUtil.getRelativePath(basePath, filePath, '/'), filePath);
    File userHomeDir = new File(SystemProperties.getUserHome());
    if (path.startsWith("..") && FileUtil.isAncestor(userHomeDir, new File(filePath), true)) {
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
    List<Navigatable> navigatables = executionNode.getNavigatables();
    if (navigatables.size() != 1) return null;
    return navigatables.get(0);
  }

  private Navigatable @Nullable [] extractSelectedNodesNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (ExecutionNode each : getSelectedNodes()) {
      List<Navigatable> navigatable = each.getNavigatables();
      navigatables.addAll(navigatable);
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
    return myTree;
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

  private @NotNull ExecutionNode getOrCreateMessagesNode(long eventTime,
                                                         String nodeId,
                                                         ExecutionNode parentNode,
                                                         String nodeName,
                                                         @Nullable @BuildEventsNls.Hint String hint,
                                                         @Nullable Supplier<? extends Icon> iconProvider,
                                                         @Nullable Navigatable navigatable,
                                                         Map<Object, ExecutionNode> nodesMap,
                                                         Project project,
                                                         @NotNull Set<? super ExecutionNode> updatedNodes) {
    ExecutionNode node = nodesMap.get(nodeId);
    if (node == null) {
      node = new ExecutionNode(project, parentNode, false, this::isCorrectThread);
      node.setName(nodeName);
      if (hint != null) {
        node.setHint(hint);
      }
      node.setStartTime(eventTime);
      node.setEndTime(eventTime);
      if (iconProvider != null) {
        node.setIconProvider(iconProvider);
      }
      if (navigatable != null) {
        node.setNavigatable(navigatable);
      }
      parentNode.add(node);
      nodesMap.put(nodeId, node);
      if (mySplitImplementation) {
        updatedNodes.add(node);
      }
    }
    return node;
  }

  @ApiStatus.Internal
  public Promise<?> invokeLater(@NotNull Runnable task) {
    return myInvoker.invokeLater(task);
  }

  void selectNode(@NotNull ExecutionNode node) {
    myConsoleViewHandler.setNodeIfChanged(node);
  }

  private static final class ConsoleViewHandler implements Disposable {
    private static final String EMPTY_CONSOLE_NAME = "empty";
    private final Project myProject;
    private final JPanel myPanel;
    private final CompositeView<ExecutionConsole> myView;
    private final AtomicReference<String> myNodeConsoleViewName = new AtomicReference<>();
    private final Map<String, List<Consumer<? super BuildTextConsoleView>>> deferredNodeOutput = new ConcurrentHashMap<>();
    private @Nullable ExecutionNode myExecutionNode;
    private final @NotNull List<? extends Filter> myExecutionConsoleFilters;
    private final BuildProgressStripe myPanelWithProgress;
    private final DefaultActionGroup myConsoleToolbarActionGroup;
    private final ActionToolbar myToolbar;

    ConsoleViewHandler(@NotNull Project project,
                       @Nullable Tree tree,
                       @NotNull ExecutionNode buildProgressRootNode,
                       @NotNull Disposable parentDisposable,
                       @Nullable ExecutionConsole executionConsole,
                       @NotNull List<? extends Filter> executionConsoleFilters) {
      myProject = project;
      myPanel = new NonOpaquePanel(new BorderLayout());
      myPanelWithProgress = new BuildProgressStripe(myPanel, parentDisposable, (int)ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS);
      myExecutionConsoleFilters = executionConsoleFilters;
      Disposer.register(parentDisposable, this);
      myView = new CompositeView<>(null) {
        @Override
        public void addView(@NotNull ExecutionConsole view, @NotNull String viewName) {
          super.addView(view, viewName);
          UIUtil.removeScrollBorder(view.getComponent());
        }
      };
      Disposer.register(this, myView);
      if (executionConsole != null) {
        String nodeConsoleViewName = getNodeConsoleViewName(buildProgressRootNode);
        myView.addViewAndShowIfNeeded(executionConsole, nodeConsoleViewName, true, false);
        myNodeConsoleViewName.set(nodeConsoleViewName);
      }
      ConsoleView emptyConsole = new ConsoleViewImpl(project, GlobalSearchScope.EMPTY_SCOPE, true, false);
      myView.addView(emptyConsole, EMPTY_CONSOLE_NAME);
      JComponent consoleComponent = emptyConsole.getComponent();
      consoleComponent.setFocusable(true);
      myPanel.add(myView.getComponent(), BorderLayout.CENTER);
      myConsoleToolbarActionGroup = new DefaultActionGroup();
      myConsoleToolbarActionGroup.copyFromGroup(createDefaultTextConsoleToolbar());
      myToolbar = ActionManager.getInstance().createActionToolbar("BuildConsole", myConsoleToolbarActionGroup, false);
      myToolbar.setTargetComponent(myView);
      myPanel.add(myToolbar.getComponent(), BorderLayout.EAST);

      if (ExperimentalUI.isNewUI()) {
        UIUtil.setBackgroundRecursively(myPanel, JBUI.CurrentTheme.ToolWindow.background());
      }

      if (tree != null) {
        tree.addTreeSelectionListener(e -> {
          if (Disposer.isDisposed(myView)) return;
          TreePath path = e.getPath();
          if (path == null) {
            return;
          }
          TreePath selectionPath = tree.getSelectionPath();
          setNode(selectionPath != null ? (DefaultMutableTreeNode)selectionPath.getLastPathComponent() : null);
        });
      }
    }

    private void showTextConsoleToolbarActions() {
      myConsoleToolbarActionGroup.copyFromGroup(createDefaultTextConsoleToolbar());
      updateToolbarActionsImmediately();
    }

    private void showCustomConsoleToolbarActions(@Nullable ActionGroup actionGroup) {
      if (actionGroup instanceof DefaultActionGroup) {
        myConsoleToolbarActionGroup.copyFromGroup((DefaultActionGroup)actionGroup);
      }
      else if (actionGroup != null) {
        myConsoleToolbarActionGroup.copyFrom(actionGroup);
      }
      else {
        myConsoleToolbarActionGroup.removeAll();
      }
      updateToolbarActionsImmediately();
    }

    private void updateToolbarActionsImmediately() {
      UIUtil.invokeLaterIfNeeded(() -> myToolbar.updateActionsImmediately());
    }

    private @NotNull DefaultActionGroup createDefaultTextConsoleToolbar() {
      DefaultActionGroup textConsoleToolbarActionGroup = new DefaultActionGroup();
      textConsoleToolbarActionGroup.add(new ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
        @Override
        protected @Nullable Editor getEditor(@NotNull AnActionEvent e) {
          var editor = ConsoleViewHandler.this.getEditor();
          if (editor == null) return null;
          return ClientEditorManager.getClientEditor(editor, ClientId.getCurrentOrNull());
        }
      });
      textConsoleToolbarActionGroup.add(new ScrollToTheEndToolbarAction(getEditor()));
      textConsoleToolbarActionGroup.add(new ClearConsoleAction());
      return textConsoleToolbarActionGroup;
    }

    private void updateProgressBar(long total, long progress) {
      myPanelWithProgress.updateProgress(total, progress);
    }

    @TestOnly
    private @NotNull ExecutionConsole getEmptyConsole() {
      return myView.getView(EMPTY_CONSOLE_NAME);
    }

    private @Nullable ExecutionConsole getCurrentConsole() {
      String nodeConsoleViewName = myNodeConsoleViewName.get();
      if (nodeConsoleViewName == null) return null;
      return myView.getView(nodeConsoleViewName);
    }

    private @Nullable Editor getEditor() {
      ExecutionConsole console = getCurrentConsole();
      if (console instanceof ConsoleViewImpl) {
        return ((ConsoleViewImpl)console).getEditor();
      }
      return null;
    }

    private void setNodeIfChanged(@NotNull ExecutionNode node) {
      if (myProject.isDisposed() || node == myExecutionNode) return;
      myExecutionNode = node;
      setNode(node);
    }

    private boolean setNode(@NotNull ExecutionNode node) {
      String nodeConsoleViewName = getNodeConsoleViewName(node);
      myNodeConsoleViewName.set(nodeConsoleViewName);
      ExecutionConsole view = myView.getView(nodeConsoleViewName);
      if (view != null) {
        List<Consumer<? super BuildTextConsoleView>> deferredOutput = deferredNodeOutput.get(nodeConsoleViewName);
        if (view instanceof BuildTextConsoleView && deferredOutput != null && !deferredOutput.isEmpty()) {
          deferredNodeOutput.remove(nodeConsoleViewName);
          deferredOutput.forEach(consumer -> consumer.accept((BuildTextConsoleView)view));
        }
        else {
          deferredNodeOutput.remove(nodeConsoleViewName);
        }
        myView.showView(nodeConsoleViewName, false);
        if (view instanceof PresentableBuildEventExecutionConsole) {
          showCustomConsoleToolbarActions(((PresentableBuildEventExecutionConsole)view).myActions);
        }
        else {
          showTextConsoleToolbarActions();
        }
        myPanel.setVisible(true);
        return true;
      }

      List<Consumer<? super BuildTextConsoleView>> deferredOutput = deferredNodeOutput.get(nodeConsoleViewName);
      if (deferredOutput != null && !deferredOutput.isEmpty()) {
        BuildTextConsoleView textConsoleView = new BuildTextConsoleView(myProject, true, myExecutionConsoleFilters);
        deferredNodeOutput.remove(nodeConsoleViewName);
        deferredOutput.forEach(consumer -> consumer.accept(textConsoleView));
        myView.addView(textConsoleView, nodeConsoleViewName);
        myView.showView(nodeConsoleViewName, false);
      }
      else {
        myView.showView(EMPTY_CONSOLE_NAME, false);
        return true;
      }
      return true;
    }

    public void maybeAddExecutionConsole(@NotNull ExecutionNode node, @NotNull BuildEventPresentationData presentationData) {
      UIUtil.invokeLaterIfNeeded(() -> {
        ExecutionConsole executionConsole = presentationData.getExecutionConsole();
        if (executionConsole == null) return;
        String nodeConsoleViewName = getNodeConsoleViewName(node);
        PresentableBuildEventExecutionConsole presentableEventView =
          new PresentableBuildEventExecutionConsole(executionConsole, presentationData.consoleToolbarActions());
        myView.addView(presentableEventView, nodeConsoleViewName);
      });
    }

    private void addOutput(@NotNull ExecutionNode node) {
      addOutput(node, view -> view.append("\n", true));
    }

    private void addOutput(@NotNull ExecutionNode node, @NotNull Object buildId, BuildEvent event) {
      addOutput(node, view -> view.onEvent(buildId, event));
    }

    private void addOutput(@NotNull ExecutionNode node, Failure failure) {
      addOutput(node, view -> view.append(failure));
    }

    private void addOutput(@NotNull ExecutionNode node, Consumer<? super BuildTextConsoleView> consumer) {
      String nodeConsoleViewName = getNodeConsoleViewName(node);
      ExecutionConsole viewView = myView.getView(nodeConsoleViewName);
      if (viewView instanceof BuildTextConsoleView) {
        consumer.accept((BuildTextConsoleView)viewView);
      }
      if (viewView == null) {
        deferredNodeOutput.computeIfAbsent(nodeConsoleViewName, s -> new ArrayList<>()).add(consumer);
      }
    }

    @Override
    public void dispose() {
      deferredNodeOutput.clear();
    }

    private void stopProgressBar() {
      myPanelWithProgress.stopLoading();
    }

    private static @NotNull String getNodeConsoleViewName(@NotNull ExecutionNode node) {
      return String.valueOf(System.identityHashCode(node));
    }

    private void setNode(@Nullable DefaultMutableTreeNode node) {
      if (myProject.isDisposed()) return;
      if (node == null || node.getUserObject() == myExecutionNode) return;
      if (node.getUserObject() instanceof ExecutionNode) {
        myExecutionNode = (ExecutionNode)node.getUserObject();
        if (setNode((ExecutionNode)node.getUserObject())) {
          return;
        }
      }

      myExecutionNode = null;
      if (myView.getView(BuildView.CONSOLE_VIEW_NAME) != null/* && myViewSettingsProvider.isSideBySideView()*/) {
        myView.showView(BuildView.CONSOLE_VIEW_NAME, false);
        myPanel.setVisible(true);
      }
      else {
        myPanel.setVisible(false);
      }
    }

    public JComponent getComponent() {
      return myPanelWithProgress;
    }

    public void clear() {
      myPanel.setVisible(false);
    }

    private static final class PresentableBuildEventExecutionConsole implements ExecutionConsole {
      private final ExecutionConsole myExecutionConsole;
      private final @Nullable ActionGroup myActions;

      private PresentableBuildEventExecutionConsole(@NotNull ExecutionConsole executionConsole,
                                                    @Nullable ActionGroup toolbarActions) {
        myExecutionConsole = executionConsole;
        myActions = toolbarActions;
      }

      @Override
      public @NotNull JComponent getComponent() {
        return myExecutionConsole.getComponent();
      }

      @Override
      public JComponent getPreferredFocusableComponent() {
        return myExecutionConsole.getPreferredFocusableComponent();
      }

      @Override
      public void dispose() {
        Disposer.dispose(myExecutionConsole);
      }
    }
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
      List<Navigatable> navigatables = executionNode.getNavigatables();
      if (!navigatables.isEmpty()) {
        return navigatables.get(0);
      }
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
      return myRootNode;
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