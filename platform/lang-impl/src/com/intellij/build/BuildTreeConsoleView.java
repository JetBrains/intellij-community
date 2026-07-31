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
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.ActionGroup;
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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import com.intellij.pom.Navigatable;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.split.SplitComponentBindingKt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
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
import javax.swing.JTree;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
  private final CoroutineScope myScope;
  private final BuildTreeViewModel myTreeVm;
  private final JComponent mySplitComponent;
  private final @NotNull ExecutionNode myRootNode;
  private final @NotNull ExecutionNode myBuildProgressRootNode;
  private final Set<Predicate<? super ExecutionNode>> myNodeFilters;
  private final OccurenceNavigator myOccurrenceNavigatorSupport;
  private final Set<BuildEvent> myDeferredEvents = ConcurrentCollectionFactory.createConcurrentSet();

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
    myRootNode.add(myBuildProgressRootNode);

    myInvoker = Invoker.forBackgroundThreadWithoutReadAction(this);

    myScope = CoroutineScopeKt.childScope(ScopeHolder.getScope(project), "BuildTreeConsoleView", EmptyCoroutineContext.INSTANCE, true);
    myTreeVm = new BuildTreeViewModel(this, myScope);
    mySplitComponent = SplitComponentBindingKt.createComponent(
      BuildTreeSplitComponentBindingKt.getBuildTreeSplitComponentBinding(),
      myProject, myScope, myTreeVm.getId()
    );

    myOccurrenceNavigatorSupport = new SplitProblemOccurrenceNavigatorSupport(myTreeVm);

    myPanel.setLayout(new BorderLayout());
    OnePixelSplitter myThreeComponentsSplitter = new OnePixelSplitter(SPLITTER_PROPERTY, SPLITTER_DEFAULT_PROPORTION);
    myThreeComponentsSplitter.setFirstComponent(mySplitComponent);
    List<Filter> filters = myBuildDescriptor.getExecutionFilters();
    myConsoleViewHandler = new BuildConsoleViewHandler(myProject, myBuildProgressRootNode, this, executionConsole, filters);
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
      myTreeVm.clearNodes();
    });
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

  @Override
  public void removeFilter(@NotNull Predicate<? super ExecutionNode> filter) {
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

  @Override
  public boolean contains(@NotNull Predicate<? super ExecutionNode> filter) {
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

    runUpdateAction(event, _ -> {
      if (event instanceof StartBuildEvent) {
        var node = getBuildProgressRootNode();
        addNode(event, node);
        node.setTitle(myBuildDescriptor.getTitle());
      }
      else {
        var parentNode = findParentNode(event);
        var node = new ExecutionNode(myProject, parentNode, false, this::isCorrectThread);
        addNode(event, node);
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
      setEndTime(node, event.getEventTime());

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
      runUpdateAction(event, _ -> {
        var parentNode = findParentNode(event);
        var node = new ExecutionNode(myProject, parentNode, isBuildProgressRootNode(parentNode), this::isCorrectThread);
        addNode(event, node);
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
          parentNode
        );
      }

      if (event instanceof DuplicateMessageAware) {
        if (parentNode != null && parentNode.findFirstChild(node -> event.getMessage().equals(node.getName())) != null) {
          return;
        }
      }

      var node = new ExecutionNode(myProject, parentNode, false, this::isCorrectThread);
      addNode(event, node);

      node.setAlwaysLeaf(event instanceof FileMessageEvent);
      node.setNavigatable(event.getNavigatable(myProject));
      setResult(node, event.getResult(), updatedNodes);
      setEndTime(node, event.getEventTime());

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

    runUpdateAction(event, _ -> {
      var node = new ExecutionNode(myProject, parentNode, isBuildProgressRootNode(parentNode), this::isCorrectThread);
      addNode(event, node);

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

    runUpdateAction(event, _ -> {});

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
    node.setResult(result);

    var parentNode = node.getParent();
    var eventKind = result instanceof MessageEventResult messageEventResult ? messageEventResult.getKind() :
                    result instanceof FailureResult ? MessageEvent.Kind.ERROR :
                    null;
    if (eventKind != null && parentNode != null) {
      reportMessageKind(eventKind, parentNode, updatedNodes);
    }
  }

  private static void setEndTime(@NotNull ExecutionNode node, @NotNull Long endTime) {
    node.setEndTime(endTime);
  }

  private void addNode(@NotNull BuildEvent event, @NotNull ExecutionNode node) {
    addNode(event.getId(), node);
  }

  private void addNode(@NotNull Object eventId, @NotNull ExecutionNode node) {
    nodesMap.put(eventId, node);
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
        executionNode.reportChildMessageKind(eventKind);
        if (executionNode != rootNode) {
          updatedNodes.add(executionNode);
        }
      }
      while ((executionNode = executionNode.getParent()) != null);
    }
  }

  private void expandFirstMessage(@NotNull ExecutionNode node) {
    if (!myExpandedFirstMessage.compareAndSet(false, true)) return;

    myTreeVm.makeVisible(node, false);
  }

  private void showErrorIfFirst(@NotNull ExecutionNode node) {
    if (!myShownFirstError.compareAndSet(false, true)) return;
    myExpandedFirstMessage.set(true);
    myTreeVm.makeVisible(node, true);
    if (myNavigateToTheFirstErrorLocation) {
      var navigatable = node.getNavigatable();
      if (navigatable != null) {
        ApplicationManager.getApplication()
          .invokeLater(() -> navigatable.navigate(true), ModalityState.defaultModalityState(), myProject.getDisposed());
      }
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
      parentNode = getOrCreateFileMessageParentNode(eventTime, filePosition, failureNavigatable, parentNode);
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
      updatedNodes.add(child);
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
    if (mySplitComponent instanceof ComponentContainer splitComponentContainer) {
      return splitComponentContainer.getPreferredFocusableComponent();
    }

    return mySplitComponent;
  }

  @Override
  public void dispose() {
    myDisposed.set(true);
    kotlinx.coroutines.CoroutineScopeKt.cancel(myScope, null);
  }

  public boolean isDisposed() {
    return myDisposed.get();
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    myInvoker.invoke(() -> onEventInternal(event));
  }

  private void runUpdateAction(@NotNull BuildEvent event, @NotNull Consumer<Set<ExecutionNode>> action) {
    var updatedNodes = new LinkedHashSet<ExecutionNode>();

    action.accept(updatedNodes);

    var node = findNode(event);
    if (node == null) {
      return;
    }

    setDefaultData(event, node);

    updatedNodes.add(node);
    myTreeVm.createOrUpdateNodes(updatedNodes);
  }

  @Contract("_, _, _, null -> null; _, _, _, !null -> !null")
  private @Nullable ExecutionNode getOrCreateFileMessageParentNode(
    long eventTime,
    @NotNull FilePosition filePosition,
    @Nullable Navigatable navigatable,
    @Nullable ExecutionNode parentNode
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
    addNode(filePath, node);
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

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, "reference.build.tool.window");
    sink.set(CommonDataKeys.PROJECT, myProject);
    sink.set(COMPONENT_KEY, this);
  }

  @ApiStatus.Internal
  public JTree getTree() {
    // won't work on rem dev backend
    return UIUtil.findComponentOfType(mySplitComponent, JTree.class);
  }

  @ApiStatus.Internal
  public void clearTreeSelection() {
    myTreeVm.clearSelection();
  }

  @ApiStatus.Internal
  public Promise<?> invokeLater(@NotNull Runnable task) {
    return myInvoker.invokeLater(task);
  }

  void selectNode(@NotNull ExecutionNode node) {
    myConsoleViewHandler.setNodeIfChanged(node);
  }

  BuildViewId getBuildViewId() {
    return myTreeVm.getId();
  }

  @NotNull
  JComponent getConsoleComponent() {
    return myConsoleViewHandler.getComponent();
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
}