// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.actions.StopAction;
import com.intellij.execution.actions.StopProcessAction;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.console.ConsoleViewWrapperBase;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentActionsContributor;
import com.intellij.execution.ui.*;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public class BuildView extends CompositeView<ExecutionConsole>
  implements BuildProgressListener, ConsoleView, Filterable<ExecutionNode>, OccurenceNavigator, ObservableConsoleView,
             RunContentActionsContributor {
  public static final String CONSOLE_VIEW_NAME = "consoleView";
  @ApiStatus.Experimental
  public static final DataKey<List<AnAction>> RESTART_ACTIONS = DataKey.create("restart actions");
  private static final OccurenceNavigator EMPTY_PROBLEMS_NAVIGATOR = new OccurenceNavigator() {
    @Override
    public boolean hasNextOccurence() {
      return false;
    }

    @Override
    public boolean hasPreviousOccurence() {
      return false;
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      return null;
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
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
  };
  private final @NotNull Project myProject;
  private final @NotNull ViewManager myViewManager;
  private final AtomicBoolean isBuildStartEventProcessed = new AtomicBoolean();
  private final List<BuildEvent> myAfterStartEvents = ContainerUtil.createConcurrentList();
  private final @NotNull DefaultBuildDescriptor myBuildDescriptor;
  private volatile @Nullable ExecutionConsole myExecutionConsole;
  private volatile BuildViewSettingsProvider myViewSettingsProvider;

  public BuildView(@NotNull Project project,
                   @NotNull BuildDescriptor buildDescriptor,
                   @NonNls @Nullable String selectionStateKey,
                   @NotNull ViewManager viewManager) {
    this(project, null, buildDescriptor, selectionStateKey, viewManager);
  }

  public BuildView(@NotNull Project project,
                   @Nullable ExecutionConsole executionConsole,
                   @NotNull BuildDescriptor buildDescriptor,
                   @NonNls @Nullable String selectionStateKey,
                   @NotNull ViewManager viewManager) {
    super(selectionStateKey);
    myProject = project;
    myViewManager = viewManager;
    myExecutionConsole = executionConsole;
    myBuildDescriptor = buildDescriptor instanceof DefaultBuildDescriptor
                        ? (DefaultBuildDescriptor)buildDescriptor
                        : new DefaultBuildDescriptor(buildDescriptor);
    Disposer.register(project, this);
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    if (event instanceof StartBuildEvent) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        onStartBuild(buildId, (StartBuildEvent)event);
        for (BuildEvent buildEvent : myAfterStartEvents) {
          processEvent(buildId, buildEvent);
        }
        myAfterStartEvents.clear();
        isBuildStartEventProcessed.set(true);
      });
      return;
    }

    if (!isBuildStartEventProcessed.get()) {
      myAfterStartEvents.add(event);
    }
    else {
      processEvent(buildId, event);
    }
  }

  private void processEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    if (event instanceof OutputBuildEvent && (event.getParentId() == null || event.getParentId() == myBuildDescriptor.getId())) {
      ExecutionConsole consoleView = getConsoleView();
      if (consoleView instanceof BuildProgressListener) {
        ((BuildProgressListener)consoleView).onEvent(buildId, event);
      }
    }
    else {
      BuildTreeConsoleView eventView = getEventView();
      if (eventView != null) {
        eventView.onEvent(buildId, event);
      }
    }
  }

  private void onStartBuild(@NotNull Object buildId, @NotNull StartBuildEvent startBuildEvent) {
    Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
      return;
    }

    if (startBuildEvent instanceof StartBuildEventImpl) {
      myViewSettingsProvider = ((StartBuildEventImpl)startBuildEvent).getBuildViewSettingsProvider();
    }
    if (myViewSettingsProvider == null) {
      myViewSettingsProvider = () -> false;
    }
    if (myExecutionConsole == null) {
      Supplier<? extends RunContentDescriptor> descriptorSupplier = myBuildDescriptor.getContentDescriptorSupplier();
      RunContentDescriptor runContentDescriptor = descriptorSupplier != null ? descriptorSupplier.get() : null;
      myExecutionConsole = runContentDescriptor != null &&
                           runContentDescriptor.getExecutionConsole() != null &&
                           runContentDescriptor.getExecutionConsole() != this ?
                           runContentDescriptor.getExecutionConsole() : new BuildTextConsoleView(myProject, false,
                                                                                                 myBuildDescriptor.getExecutionFilters());
      if (runContentDescriptor != null && runContentDescriptor.getExecutionConsole() != this) {
        Disposer.register(this, runContentDescriptor);
      }
    }
    boolean buildTree = true;
    ExecutionConsole executionConsole = myExecutionConsole;
    if (executionConsole != null) {
      executionConsole.getComponent(); //create editor to be able to add console editor actions
      if (myViewSettingsProvider.isExecutionViewHidden()) {
        addViewAndShowIfNeeded(executionConsole, CONSOLE_VIEW_NAME, myViewManager.isConsoleEnabledByDefault(), false);
        buildTree = false;
      }
      else if (isShowInDashboard()) {
        ExecutionConsole consoleView =
          executionConsole instanceof ConsoleView ? wrapWithToolbar((ConsoleView)executionConsole) : executionConsole;
        addViewAndShowIfNeeded(consoleView, CONSOLE_VIEW_NAME, myViewManager.isConsoleEnabledByDefault(), false);
        if (executionConsole instanceof ConsoleViewImpl consoleViewImpl) {
          consoleViewImpl.getEditor().setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
        }
        buildTree = false;
      }
    }

    BuildTreeConsoleView eventView = null;
    if (buildTree) {
      eventView = getEventView();
      if (eventView == null) {
        String eventViewName = BuildTreeConsoleView.class.getName();
        eventView = new BuildTreeConsoleView(myProject, myBuildDescriptor, myExecutionConsole);
        addView(eventView, eventViewName);
        showView(eventViewName, false);
      }
    }

    BuildProcessHandler processHandler = myBuildDescriptor.getProcessHandler();
    if (myExecutionConsole instanceof ConsoleView consoleView) {
      if (!(consoleView instanceof BuildTextConsoleView)) {
        myBuildDescriptor.getExecutionFilters().forEach(consoleView::addMessageFilter);
      }

      if (processHandler != null) {
        assert consoleView != null;
        consoleView.attachToProcess(processHandler);
        Consumer<? super ConsoleView> attachedConsoleConsumer = myBuildDescriptor.getAttachedConsoleConsumer();
        if (attachedConsoleConsumer != null) {
          attachedConsoleConsumer.consume(consoleView);
        }
      }
    }
    if (processHandler != null && !processHandler.isStartNotified()) {
      processHandler.startNotify();
    }

    if (eventView != null) {
      eventView.onEvent(buildId, startBuildEvent);
    }
  }

  @ApiStatus.Internal
  public @Nullable ExecutionConsole getConsoleView() {
    return myExecutionConsole;
  }

  @ApiStatus.Internal
  public @NotNull List<AnAction> getRestartActions() {
    return myBuildDescriptor.getRestartActions();
  }

  @Nullable
  @ApiStatus.Internal
  BuildTreeConsoleView getEventView() {
    return getView(BuildTreeConsoleView.class.getName(), BuildTreeConsoleView.class);
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    ExecutionConsole console = getConsoleView();
    if (console instanceof ObservableConsoleView) {
      ((ObservableConsoleView) console).addChangeListener(listener, parent);
    }
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    delegateToConsoleView(view -> view.print(text, contentType));
  }

  private void delegateToConsoleView(Consumer<? super ConsoleView> viewConsumer) {
    ExecutionConsole console = getConsoleView();
    if (console instanceof ConsoleView) {
      viewConsumer.consume((ConsoleView)console);
    }
  }

  private @Nullable <R> R getConsoleViewValue(Function<? super ConsoleView, ? extends R> viewConsumer) {
    ExecutionConsole console = getConsoleView();
    if (console instanceof ConsoleView) {
      return viewConsumer.apply((ConsoleView)console);
    }
    return null;
  }

  @Override
  public void clear() {
    delegateToConsoleView(ConsoleView::clear);
  }

  @Override
  public void scrollTo(int offset) {
    delegateToConsoleView(view -> view.scrollTo(offset));
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    delegateToConsoleView(view -> view.attachToProcess(processHandler));
  }

  @Override
  public boolean isOutputPaused() {
    Boolean result = getConsoleViewValue(ConsoleView::isOutputPaused);
    return result != null && result;
  }

  @Override
  public void setOutputPaused(boolean value) {
    delegateToConsoleView(view -> view.setOutputPaused(value));
  }

  @Override
  public boolean hasDeferredOutput() {
    Boolean result = getConsoleViewValue(ConsoleView::hasDeferredOutput);
    return result != null && result;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
    delegateToConsoleView(view -> view.performWhenNoDeferredOutput(runnable));
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
    delegateToConsoleView(view -> view.setHelpId(helpId));
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    delegateToConsoleView(view -> view.addMessageFilter(filter));
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {
    delegateToConsoleView(view -> view.printHyperlink(hyperlinkText, info));
  }

  @Override
  public int getContentSize() {
    Integer result = getConsoleViewValue(ConsoleView::getContentSize);
    return result == null ? 0 : result;
  }

  @Override
  public boolean canPause() {
    Boolean result = getConsoleViewValue(ConsoleView::canPause);
    return result != null && result;
  }

  @Override
  public AnAction @NotNull [] createConsoleActions() {
    if (!myViewManager.isBuildContentView()) {
      // console actions should be integrated with the provided toolbar when the console is shown not on Build tw
      return AnAction.EMPTY_ARRAY;
    }
    final DefaultActionGroup rerunActionGroup = new DefaultActionGroup();
    AnAction stopAction = null;
    if (myBuildDescriptor.getProcessHandler() != null) {
      stopAction = new StopProcessAction(IdeBundle.messagePointer("action.DumbAware.BuildView.text.stop"),
                                         IdeBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.description.stop"),
                                         myBuildDescriptor.getProcessHandler());
      ActionUtil.copyFrom(stopAction, IdeActions.ACTION_STOP_PROGRAM);
      stopAction.registerCustomShortcutSet(stopAction.getShortcutSet(), this);
    }

    ExecutionConsole consoleView = getConsoleView();
    if (consoleView instanceof ConsoleView) {
      consoleView.getComponent(); //create editor to be able to add console editor actions
      if (stopAction == null) {
        final AnAction[] consoleActions = ((ConsoleView)consoleView).createConsoleActions();
        stopAction = ContainerUtil.find(consoleActions, StopAction.class::isInstance);
      }
    }
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (AnAction anAction : myBuildDescriptor.getRestartActions()) {
      rerunActionGroup.add(anAction);
    }

    if (stopAction != null) {
      rerunActionGroup.add(stopAction);
    }
    actionGroup.add(rerunActionGroup);
    final DefaultActionGroup otherActionGroup = new DefaultActionGroup();

    List<AnAction> otherActions = myBuildDescriptor.getActions();
    if (!otherActions.isEmpty()) {
      otherActionGroup.addSeparator();
      for (AnAction anAction : otherActions) {
        otherActionGroup.add(anAction);
      }
      otherActionGroup.addSeparator();
    }
    return new AnAction[]{actionGroup, otherActionGroup};
  }

  @Override
  public void allowHeavyFilters() {
    delegateToConsoleView(ConsoleView::allowHeavyFilters);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    ExecutionConsole consoleView = getConsoleView();
    sink.set(LangDataKeys.CONSOLE_VIEW, consoleView instanceof ConsoleView o ? o : null);

    ExecutionEnvironment environment = myBuildDescriptor.getExecutionEnvironment();
    sink.set(LangDataKeys.RUN_PROFILE, environment == null ? null : environment.getRunProfile());
    sink.set(ExecutionDataKeys.EXECUTION_ENVIRONMENT, myBuildDescriptor.getExecutionEnvironment());
    sink.set(RESTART_ACTIONS, myBuildDescriptor.getRestartActions());
  }

  @Override
  public boolean isFilteringEnabled() {
    return getEventView() != null;
  }

  @Override
  public @NotNull Predicate<ExecutionNode> getFilter() {
    BuildTreeConsoleView eventView = getEventView();
    return eventView == null ? executionNode -> true : eventView.getFilter();
  }

  @Override
  public void addFilter(@NotNull Predicate<? super ExecutionNode> filter) {
    BuildTreeConsoleView eventView = getEventView();
    if (eventView != null) {
      eventView.addFilter(filter);
    }
  }

  @Override
  public void removeFilter(@NotNull Predicate<? super ExecutionNode> filter) {
    BuildTreeConsoleView eventView = getEventView();
    if (eventView != null) {
      eventView.removeFilter(filter);
    }
  }

  @Override
  public boolean contains(@NotNull Predicate<? super ExecutionNode> filter) {
    BuildTreeConsoleView eventView = getEventView();
    return eventView != null && eventView.contains(filter);
  }

  private @NotNull OccurenceNavigator getOccurenceNavigator() {
    BuildTreeConsoleView eventView = getEventView();
    if (eventView != null) return eventView;
    ExecutionConsole executionConsole = getConsoleView();
    if (executionConsole instanceof OccurenceNavigator) {
      return (OccurenceNavigator)executionConsole;
    }
    return EMPTY_PROBLEMS_NAVIGATOR;
  }

  @Override
  public boolean hasNextOccurence() {
    return getOccurenceNavigator().hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return getOccurenceNavigator().hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return getOccurenceNavigator().goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return getOccurenceNavigator().goPreviousOccurence();
  }

  @Override
  public @NotNull String getNextOccurenceActionName() {
    return getOccurenceNavigator().getNextOccurenceActionName();
  }

  @Override
  public @NotNull String getPreviousOccurenceActionName() {
    return getOccurenceNavigator().getPreviousOccurenceActionName();
  }

  private boolean isShowInDashboard() {
    ExecutionEnvironment environment = myBuildDescriptor.getExecutionEnvironment();
    RunProfile runProfile = environment != null ? environment.getRunProfile() : null;
    return runProfile instanceof RunConfiguration configuration &&
           RunDashboardManager.getInstance(myProject).isShowInDashboard(configuration) &&
           ExecutionManagerImpl.getDelegatedRunProfile(configuration) instanceof RunConfiguration;
  }

  @Override
  public @NotNull AnAction @NotNull[] getActions() {
    return myExecutionConsole instanceof RunContentActionsContributor c ? c.getActions() : AnAction.EMPTY_ARRAY;
  }

  @Override
  public @NotNull AnAction @NotNull[] getAdditionalActions() {
    return myExecutionConsole instanceof RunContentActionsContributor c ? c.getAdditionalActions() : AnAction.EMPTY_ARRAY;
  }

  @Override
  public void hideOriginalActions() {
    if (myExecutionConsole instanceof RunContentActionsContributor c) c.hideOriginalActions();
  }

  private static @NotNull ExecutionConsole wrapWithToolbar(@NotNull ConsoleView executionConsole) {
    return new ConsoleViewWrapperBase(executionConsole) {
      private final JPanel myPanel;
      {
        myPanel = new NonOpaquePanel(new BorderLayout());
        JComponent baseComponent = getDelegate().getComponent();
        myPanel.add(baseComponent, BorderLayout.CENTER);

        DefaultActionGroup actionGroup = new DefaultActionGroup(executionConsole.createConsoleActions());
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("BuildConsole", actionGroup, false);
        toolbar.setTargetComponent(baseComponent);
        myPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      }

      @Override
      public @NotNull JComponent getComponent() {
        return myPanel;
      }
    };
  }
}