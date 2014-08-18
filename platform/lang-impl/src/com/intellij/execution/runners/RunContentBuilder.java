/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class RunContentBuilder extends RunTab {
  @NonNls private static final String JAVA_RUNNER = "JavaRunner";

  private final List<AnAction> myRunnerActions = new SmartList<AnAction>();
  private final ExecutionResult myExecutionResult;

  /**
   * @deprecated use {@link #RunContentBuilder(com.intellij.execution.ExecutionResult, ExecutionEnvironment)}
   * to remove in IDEA 14
   */
  @SuppressWarnings("UnusedParameters")
  public RunContentBuilder(@NotNull Project project,
                           ProgramRunner runner,
                           Executor executor,
                           ExecutionResult executionResult,
                           @NotNull ExecutionEnvironment environment) {
    //noinspection deprecation
    this(runner, executionResult, environment);
  }

  /**
   * @deprecated use {@link #RunContentBuilder(com.intellij.execution.ExecutionResult, ExecutionEnvironment)}
   * to remove in IDEA 15
   */
  public RunContentBuilder(ProgramRunner runner,
                           ExecutionResult executionResult,
                           @NotNull ExecutionEnvironment environment) {
    this(executionResult, fix(environment, runner));
  }

  public RunContentBuilder(@NotNull ExecutionResult executionResult, @NotNull ExecutionEnvironment environment) {
    super(environment, getRunnerType(executionResult.getExecutionConsole()));

    myExecutionResult = executionResult;
    myUi.getOptions().setMoveToGridActionEnabled(false).setMinimizeActionEnabled(false);
  }

  @NotNull
  public static ExecutionEnvironment fix(@NotNull ExecutionEnvironment environment, @Nullable ProgramRunner runner) {
    if (runner == null || runner.equals(environment.getRunner())) {
      return environment;
    }
    else {
      return new ExecutionEnvironmentBuilder(environment).runner(runner).build();
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  @NotNull
  /**
   * @deprecated to remove in IDEA 15
   */
  public static GlobalSearchScope createSearchScope(Project project, RunProfile runProfile) {
    return SearchScopeProvider.createSearchScope(project, runProfile);
  }

  @NotNull
  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  public void addAction(@NotNull final AnAction action) {
    myRunnerActions.add(action);
  }

  @NotNull
  private RunContentDescriptor createDescriptor() {
    final RunProfile profile = getEnvironment().getRunProfile();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      RunContentDescriptor contentDescriptor = new RunContentDescriptor(profile, myExecutionResult, myUi);
      Disposer.register(contentDescriptor, this);
      return contentDescriptor;
    }

    final ExecutionConsole console = myExecutionResult.getExecutionConsole();
    if (console != null) {
      if (console instanceof ExecutionConsoleEx) {
        ((ExecutionConsoleEx)console).buildUi(myUi);
      }
      else {
        buildConsoleUiDefault(myUi, console);
      }
      initLogConsoles(profile, myExecutionResult.getProcessHandler(), console);
    }
    RunContentDescriptor contentDescriptor = new RunContentDescriptor(profile, myExecutionResult, myUi);
    Disposer.register(contentDescriptor, this);
    myUi.getOptions().setLeftToolbar(createActionToolbar(contentDescriptor), ActionPlaces.UNKNOWN);

    if (profile instanceof RunConfigurationBase) {
      if (console instanceof ObservableConsoleView && !ApplicationManager.getApplication().isUnitTestMode()) {
        ((ObservableConsoleView)console).addChangeListener(new ConsoleToFrontListener((RunConfigurationBase)profile,
                                                                                      getProject(),
                                                                                      getEnvironment().getExecutor(),
                                                                                      contentDescriptor,
                                                                                      myUi),
                                                           this);
      }
    }

    return contentDescriptor;
  }

  @NotNull
  private static String getRunnerType(@Nullable ExecutionConsole console) {
    String runnerType = JAVA_RUNNER;
    if (console instanceof ExecutionConsoleEx) {
      String id = ((ExecutionConsoleEx)console).getExecutionConsoleId();
      if (id != null) {
        return JAVA_RUNNER + '.' + id;
      }
    }
    return runnerType;
  }

  public static void buildConsoleUiDefault(RunnerLayoutUi ui, final ExecutionConsole console) {
    final Content consoleContent = ui.createContent(ExecutionConsole.CONSOLE_CONTENT_ID, console.getComponent(), "Console",
                                                    AllIcons.Debugger.Console,
                                                    console.getPreferredFocusableComponent());

    consoleContent.setCloseable(false);
    addAdditionalConsoleEditorActions(console, consoleContent);
    ui.addContent(consoleContent, 0, PlaceInGrid.bottom, false);
  }

  public static void addAdditionalConsoleEditorActions(final ExecutionConsole console, final Content consoleContent) {
    final DefaultActionGroup consoleActions = new DefaultActionGroup();
    if (console instanceof ConsoleView) {
      for (AnAction action : ((ConsoleView)console).createConsoleActions()) {
        consoleActions.add(action);
      }
    }

    consoleContent.setActions(consoleActions, ActionPlaces.UNKNOWN, console.getComponent());
  }

  @NotNull
  private ActionGroup createActionToolbar(@NotNull RunContentDescriptor contentDescriptor) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN));
    if (myExecutionResult instanceof DefaultExecutionResult) {
      final AnAction[] actions = ((DefaultExecutionResult)myExecutionResult).getRestartActions();
      if (actions != null) {
        actionGroup.addAll(actions);
        if (actions.length > 0) {
          actionGroup.addSeparator();
        }
      }
    }

    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM));
    if (myExecutionResult instanceof DefaultExecutionResult) {
      actionGroup.addAll(((DefaultExecutionResult)myExecutionResult).getAdditionalStopActions());
    }

    actionGroup.addAll(myExecutionResult.getActions());

    for (AnAction anAction : myRunnerActions) {
      if (anAction != null) {
        actionGroup.add(anAction);
      }
      else {
        actionGroup.addSeparator();
      }
    }

    actionGroup.addSeparator();
    actionGroup.add(myUi.getOptions().getLayoutActions());
    actionGroup.addSeparator();
    actionGroup.add(PinToolwindowTabAction.getPinAction());
    actionGroup.add(new CloseAction(getEnvironment().getExecutor(), contentDescriptor, getProject()));
    final String helpId = contentDescriptor.getHelpId();
    actionGroup.add(new ContextHelpAction(helpId != null ? helpId : getEnvironment().getExecutor().getHelpId()));
    return actionGroup;
  }

  @Override
  public ProcessHandler getProcessHandler() {
    return myExecutionResult.getProcessHandler();
  }

  /**
   * @param reuseContent see {@link RunContentDescriptor#myContent}
   */
  public RunContentDescriptor showRunContent(@Nullable RunContentDescriptor reuseContent) {
    RunContentDescriptor descriptor = createDescriptor();
    RunContentManagerImpl.copyContentAndBehavior(descriptor, reuseContent);
    myRunContentDescriptor = descriptor;
    return descriptor;
  }

  @Override
  protected RunnerLayoutUi getUi() {
    return myUi;
  }

  @Override
  protected Icon getDefaultIcon() {
    return AllIcons.Debugger.Console;
  }

  public static class ConsoleToFrontListener implements ConsoleViewImpl.ChangeListener {
    @NotNull private final RunConfigurationBase myRunConfigurationBase;
    @NotNull private final Project myProject;
    @NotNull private final Executor myExecutor;
    @NotNull private final RunContentDescriptor myRunContentDescriptor;
    @NotNull private final RunnerLayoutUi myUi;

    public ConsoleToFrontListener(@NotNull RunConfigurationBase runConfigurationBase,
                                  @NotNull Project project,
                                  @NotNull Executor executor,
                                  @NotNull RunContentDescriptor runContentDescriptor,
                                  @NotNull RunnerLayoutUi ui) {
      myRunConfigurationBase = runConfigurationBase;
      myProject = project;
      myExecutor = executor;
      myRunContentDescriptor = runContentDescriptor;
      myUi = ui;
    }

    @Override
    public void contentAdded(Collection<ConsoleViewContentType> types) {
      if (myProject.isDisposed() || myUi.isDisposed())
        return;
      for (ConsoleViewContentType type : types) {
        if ((type == ConsoleViewContentType.NORMAL_OUTPUT) && myRunConfigurationBase.isShowConsoleOnStdOut()
            || (type == ConsoleViewContentType.ERROR_OUTPUT) && myRunConfigurationBase.isShowConsoleOnStdErr()) {
          ExecutionManager.getInstance(myProject).getContentManager().toFrontRunContent(myExecutor, myRunContentDescriptor);
          myUi.selectAndFocus(myUi.findContent(ExecutionConsole.CONSOLE_CONTENT_ID), false, false);
          return;
        }
      }
    }
  }
}
