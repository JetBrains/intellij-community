// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.CreateAction;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.customization.CustomActionsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.DefaultActionGroupWithDelegate;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.MoreActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.content.SingleContentSupplier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static com.intellij.openapi.actionSystem.Anchor.AFTER;

/**
 * Responsible for building the content of the Run or Debug tool window.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
 */
public final class RunContentBuilder extends RunTab {
  @ApiStatus.Experimental
  public static final String RUN_TOOL_WINDOW_TOP_TOOLBAR_OLD_GROUP = "RunTab.TopToolbar.Old";
  @ApiStatus.Experimental
  public static final String RUN_TOOL_WINDOW_TOP_TOOLBAR_GROUP = "RunTab.TopToolbar";
  @ApiStatus.Experimental
  public static final String RUN_TOOL_WINDOW_TOP_TOOLBAR_MORE_GROUP = "RunTab.TopToolbar.More";

  private static final String JAVA_RUNNER = "JavaRunner";

  private final List<AnAction> myRunnerActions = new SmartList<>();
  private final ExecutionResult myExecutionResult;
  private DefaultActionGroup toolbar = null;

  public RunContentBuilder(@NotNull ExecutionResult executionResult, @NotNull ExecutionEnvironment environment) {
    super(environment, getRunnerType(executionResult.getExecutionConsole()));

    myExecutionResult = executionResult;
    myUi.getOptions().setMoveToGridActionEnabled(false).setMinimizeActionEnabled(false);
  }

  private @Nullable SingleContentSupplier mySupplier;

  public static @NotNull ExecutionEnvironment fix(@NotNull ExecutionEnvironment environment, @Nullable ProgramRunner runner) {
    if (runner == null || runner.equals(environment.getRunner())) {
      return environment;
    }
    else {
      return new ExecutionEnvironmentBuilder(environment).runner(runner).build();
    }
  }

  public void addAction(final @NotNull AnAction action) {
    myRunnerActions.add(action);
  }

  private @NotNull RunContentDescriptor createDescriptor() {
    RunProfile profile = myEnvironment.getRunProfile();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new RunContentDescriptor(profile, myExecutionResult, myUi);
    }

    ExecutionConsole console = myExecutionResult.getExecutionConsole();
    RunContentDescriptor contentDescriptor = new RunContentDescriptor(profile, myExecutionResult, myUi);
    AnAction[] consoleActionsToMerge;
    AnAction[] additionalActionsToMerge;
    Content consoleContent = null;
    if (console != null) {
      if (console instanceof ExecutionConsoleEx) {
        ((ExecutionConsoleEx)console).buildUi(myUi);
      }
      else {
        consoleContent = buildConsoleUiDefault(myUi, console);
      }
      initLogConsoles(profile, contentDescriptor, console);
    }
    if (consoleContent != null && myUi.getContentManager().getContentCount() == 1 && console instanceof TerminalExecutionConsole) {
      // TerminalExecutionConsole provides too few toolbar actions. Such console toolbar doesn't look good, but occupy
      // valuable space.
      // Let's show terminal console actions right in the main toolbar iff console content is the only one.
      // Otherwise (with multiple contents), it's unclear what content will be affected by a console action from
      // the main toolbar.
      consoleActionsToMerge = ((TerminalExecutionConsole)console).createConsoleActions();
      // clear console toolbar actions to remove the console toolbar
      consoleContent.setActions(new DefaultActionGroup(), ActionPlaces.RUNNER_TOOLBAR, console.getComponent());
      additionalActionsToMerge = AnAction.EMPTY_ARRAY;
    }
    else if (consoleContent != null && myUi.getContentManager().getContentCount() == 1 && console instanceof RunContentActionsContributor contributor) {
      consoleActionsToMerge = contributor.getActions();
      additionalActionsToMerge = contributor.getAdditionalActions();
      contributor.hideOriginalActions();
    }
    else {
      consoleActionsToMerge = AnAction.EMPTY_ARRAY;
      additionalActionsToMerge = AnAction.EMPTY_ARRAY;
    }

    AnAction[] restartActions = contentDescriptor.getRestartActions();
    initToolbars(restartActions, consoleActionsToMerge, additionalActionsToMerge);
    CustomActionsListener.subscribe(this, () -> {
      DefaultActionGroup updatedToolbar = createActionToolbar(restartActions, consoleActionsToMerge, additionalActionsToMerge);
      toolbar.removeAll();
      toolbar.addAll(updatedToolbar.getChildren(null));
    });

    if (profile instanceof RunConfigurationBase) {
      if (console instanceof ObservableConsoleView && !ApplicationManager.getApplication().isUnitTestMode()) {
        ((ObservableConsoleView)console).addChangeListener(new ConsoleToFrontListener((RunConfigurationBase)profile,
                                                                                      myProject,
                                                                                      myEnvironment.getExecutor(),
                                                                                      contentDescriptor,
                                                                                      myUi), this);
      }
    }

    return contentDescriptor;
  }

  private void initToolbars(AnAction @NotNull [] restartActions, AnAction @NotNull [] consoleActions, AnAction @NotNull [] additionalActions) {
    toolbar = createActionToolbar(restartActions, consoleActions, additionalActions);
    if (UIExperiment.isNewDebuggerUIEnabled()) {
      var isVerticalToolbar = Registry.get("debugger.new.tool.window.layout.toolbar").isOptionEnabled("Vertical");

      if (Registry.is("debugger.new.tool.window.layout.single.content", false)) {
        mySupplier = new RunTabSupplier(toolbar) {
          {
            setMoveToolbar(!isVerticalToolbar);
          }

          @Override
          public @Nullable ActionGroup getToolbarActions() {
            return isVerticalToolbar ? ActionGroup.EMPTY_GROUP : super.getToolbarActions();
          }

          @Override
          public @NotNull List<AnAction> getContentActions() {
            return List.of(myUi.getOptions().getLayoutActions());
          }

          @Override
          public @NotNull String getMainToolbarPlace() {
            return ActionPlaces.RUNNER_TOOLBAR;
          }

          @Override
          public @NotNull String getContentToolbarPlace() {
            return ActionPlaces.RUNNER_TOOLBAR;
          }
        };
      }
      if (myUi instanceof RunnerLayoutUiImpl) {
        ((RunnerLayoutUiImpl)myUi).setLeftToolbarVisible(isVerticalToolbar);
      }
      if (isVerticalToolbar) {
        myUi.getOptions().setLeftToolbar(toolbar, ActionPlaces.RUNNER_TOOLBAR);
      } else {
        // wrapped into DefaultActionGroup to prevent loading all actions instantly
        DefaultActionGroup topToolbar = new DefaultActionGroupWithDelegate(toolbar);
        topToolbar.add(new EmptyWhenDuplicate(toolbar, group -> group instanceof RunTab.ToolbarActionGroup));
        myUi.getOptions().setTopLeftToolbar(topToolbar, ActionPlaces.RUNNER_TOOLBAR);
      }
    } else {
      myUi.getOptions().setLeftToolbar(toolbar, ActionPlaces.RUNNER_TOOLBAR);
    }
  }

  @Override
  protected @Nullable SingleContentSupplier getSupplier() {
    return mySupplier;
  }

  private static @NotNull String getRunnerType(@Nullable ExecutionConsole console) {
    if (console instanceof ExecutionConsoleEx) {
      String id = ((ExecutionConsoleEx)console).getExecutionConsoleId();
      if (id != null) {
        return JAVA_RUNNER + '.' + id;
      }
    }
    return JAVA_RUNNER;
  }

  public static @NotNull Content buildConsoleUiDefault(@NotNull RunnerLayoutUi ui, @NotNull ExecutionConsole console) {
    Content consoleContent = ui.createContent(ExecutionConsole.CONSOLE_CONTENT_ID, console.getComponent(),
                                              CommonBundle.message("title.console"),
                                              null,
                                              console.getPreferredFocusableComponent());

    consoleContent.setCloseable(false);
    addAdditionalConsoleEditorActions(console, consoleContent);
    ui.addContent(consoleContent, 0, PlaceInGrid.bottom, false);
    return consoleContent;
  }

  public static void addAdditionalConsoleEditorActions(final ExecutionConsole console, final Content consoleContent) {
    final DefaultActionGroup consoleActions = new DefaultActionGroup();
    if (console instanceof ConsoleView) {
      for (AnAction action : ((ConsoleView)console).createConsoleActions()) {
        consoleActions.add(action);
      }
    }

    consoleContent.setActions(consoleActions, ActionPlaces.RUNNER_TOOLBAR, console.getComponent());
  }

  private @NotNull DefaultActionGroup createActionToolbar(AnAction @NotNull [] restartActions, AnAction @NotNull [] consoleActions, AnAction @NotNull [] additionalActions) {
    boolean isNewLayout = UIExperiment.isNewDebuggerUIEnabled();

    String mainGroupId = isNewLayout ? RUN_TOOL_WINDOW_TOP_TOOLBAR_GROUP : RUN_TOOL_WINDOW_TOP_TOOLBAR_OLD_GROUP;
    ActionGroup toolbarGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(mainGroupId);
    AnAction[] mainChildren = toolbarGroup.getChildren(null);
    DefaultActionGroup actionGroup = new DefaultActionGroupWithDelegate(toolbarGroup);
    addAvoidingDuplicates(actionGroup, mainChildren);

    DefaultActionGroup afterRunActions = new DefaultActionGroup(restartActions);
    if (!isNewLayout) {
      afterRunActions.add(new CreateAction(AllIcons.General.Settings));
      afterRunActions.addSeparator();
    }

    MoreActionGroup moreGroup = null;
    if (isNewLayout) {
      moreGroup = new MoreActionGroup();
      ActionGroup moreActionGroup =
        (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(RUN_TOOL_WINDOW_TOP_TOOLBAR_MORE_GROUP);
      addAvoidingDuplicates(moreGroup, moreActionGroup.getChildren(null), mainChildren);
    }

    addActionsWithConstraints(afterRunActions.getChildren(null), new Constraints(AFTER, IdeActions.ACTION_RERUN), actionGroup, moreGroup);

    DefaultActionGroup afterStopActions = new DefaultActionGroup(myExecutionResult.getActions());
    if (consoleActions.length > 0) {
      afterStopActions.addSeparator();
      afterStopActions.addAll(consoleActions);
    }

    if (!isNewLayout) {
      for (AnAction anAction : myRunnerActions) {
        if (anAction != null) {
          afterStopActions.add(anAction);
        }
        else {
          afterStopActions.addSeparator();
        }
      }

      addActionsWithConstraints(afterStopActions.getChildren(null), new Constraints(AFTER, IdeActions.ACTION_STOP_PROGRAM),
                                actionGroup, null);

      actionGroup.addSeparator();
      actionGroup.add(myUi.getOptions().getLayoutActions());
      actionGroup.addSeparator();
      actionGroup.add(PinToolwindowTabAction.getPinAction());
    }
    else {
      afterStopActions.addSeparator();
      afterStopActions.addAll(myRunnerActions);
      addActionsWithConstraints(afterStopActions.getChildren(null), new Constraints(AFTER, IdeActions.ACTION_STOP_PROGRAM),
                                actionGroup, moreGroup);
      moreGroup.addSeparator();

      if (additionalActions.length > 0) {
        moreGroup.addSeparator();
        moreGroup.addAll(additionalActions);
      }

      actionGroup.add(moreGroup);
      moreGroup.add(new CreateAction());
    }
    return actionGroup;
  }

  /**
   * @param reuseContent see {@link RunContentDescriptor#myContent}
   */
  public RunContentDescriptor showRunContent(@Nullable RunContentDescriptor reuseContent) {
    RunContentDescriptor descriptor = createDescriptor();
    Disposer.register(descriptor, this);
    Disposer.register(myProject, descriptor);
    RunContentManagerImpl.copyContentAndBehavior(descriptor, reuseContent);
    myRunContentDescriptor = descriptor;
    return descriptor;
  }

  public static final class ConsoleToFrontListener implements ObservableConsoleView.ChangeListener {
    private final @NotNull Project myProject;
    private final @NotNull Executor myExecutor;
    private final @NotNull RunContentDescriptor myRunContentDescriptor;
    private final @NotNull RunnerLayoutUi myUi;
    private final boolean myShowConsoleOnStdOut;
    private final boolean myShowConsoleOnStdErr;
    private final AtomicBoolean myFocused = new AtomicBoolean();

    public ConsoleToFrontListener(@NotNull RunConfigurationBase runConfigurationBase,
                                  @NotNull Project project,
                                  @NotNull Executor executor,
                                  @NotNull RunContentDescriptor runContentDescriptor,
                                  @NotNull RunnerLayoutUi ui) {
      myProject = project;
      myExecutor = executor;
      myRunContentDescriptor = runContentDescriptor;
      myUi = ui;
      myShowConsoleOnStdOut = runConfigurationBase.isShowConsoleOnStdOut();
      myShowConsoleOnStdErr = runConfigurationBase.isShowConsoleOnStdErr();
    }

    @Override
    public void textAdded(@NotNull String text, @NotNull ConsoleViewContentType type) {
      if (myProject.isDisposed() || myUi.isDisposed()) {
        return;
      }
      if (((type == ConsoleViewContentType.NORMAL_OUTPUT) && myShowConsoleOnStdOut
           || (type == ConsoleViewContentType.ERROR_OUTPUT) && myShowConsoleOnStdErr) && myFocused.compareAndSet(false, true)) {
        RunContentManager.getInstance(myProject).toFrontRunContent(myExecutor, myRunContentDescriptor);
        myUi.selectAndFocus(myUi.findContent(ExecutionConsole.CONSOLE_CONTENT_ID), false, false);
      }
    }
  }

  private static final class EmptyWhenDuplicate extends ActionGroup implements ActionWithDelegate<ActionGroup> {

    private final @NotNull ActionGroup myDelegate;
    private final @NotNull Predicate<? super ActionGroup> myDuplicatePredicate;

    private EmptyWhenDuplicate(@NotNull ActionGroup delegate, @NotNull Predicate<? super ActionGroup> isDuplicate) {
      myDelegate = delegate;
      myDuplicatePredicate = isDuplicate;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (isToolbarDuplicatedAnywhere(getEventComponent(e))) {
        return AnAction.EMPTY_ARRAY;
      }
      return myDelegate.getChildren(e);
    }

    @Nullable
    private static Component getEventComponent(@Nullable AnActionEvent e) {
      if (e == null) return null;
      SingleContentSupplier supplier = e.getData(SingleContentSupplier.KEY);
      return supplier != null ? supplier.getTabs().getComponent() : null;
    }

    private boolean isToolbarDuplicatedAnywhere(@Nullable Component parent) {
      for (Component component : UIUtil.uiTraverser(parent)) {
        if (component instanceof ActionToolbarImpl) {
          ActionGroup group = ((ActionToolbarImpl)component).getActionGroup();
          if (myDuplicatePredicate.test(group)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public @NotNull ActionGroup getDelegate() {
      return myDelegate;
    }
  }

  public GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }


  public static void addActionsWithConstraints(AnAction[] actions,
                                               Constraints constraints,
                                               DefaultActionGroup actionGroup,
                                               @Nullable DefaultActionGroup moreGroup) {
    addActionsWithConstraints(Arrays.asList(actions), constraints, actionGroup, moreGroup);
  }

  public static void addActionsWithConstraints(List<AnAction> actions,
                                               Constraints constraints,
                                               DefaultActionGroup actionGroup,
                                               @Nullable DefaultActionGroup moreGroup) {
    for (AnAction action : ContainerUtil.reverse(actions)) {
      if (moreGroup != null && action.getTemplatePresentation().getClientProperty(PREFERRED_PLACE) == PreferredPlace.MORE_GROUP) {
        addAvoidingDuplicates(moreGroup, new AnAction[]{action}, Constraints.LAST, AnAction.EMPTY_ARRAY);
      }
      else {
        addAvoidingDuplicates(actionGroup, new AnAction[]{action}, constraints, AnAction.EMPTY_ARRAY);
      }
    }
  }

  private static void addAvoidingDuplicates(DefaultActionGroup group,
                                            AnAction[] actions,
                                            Constraints constraints,
                                            AnAction[] existingActions) {
    HashSet<AnAction> visited = ContainerUtil.newHashSet(existingActions);
    for (AnAction action : actions) {
      if (action instanceof Separator || (!visited.contains(action) && !group.containsAction(action))) {
        group.add(action, constraints);
      }
    }
  }

  public static void addAvoidingDuplicates(DefaultActionGroup group, AnAction[] actions, AnAction[] existingActions) {
    addAvoidingDuplicates(group, actions, Constraints.LAST, existingActions);
  }

  public static void addAvoidingDuplicates(DefaultActionGroup group, AnAction[] actions) {
    addAvoidingDuplicates(group, actions, Constraints.LAST, AnAction.EMPTY_ARRAY);
  }
}
