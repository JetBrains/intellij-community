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
package com.intellij.execution.runners;

import com.intellij.diagnostic.logging.LogConsoleManagerBase;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.diagnostic.logging.OutputFileUtil;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author dyoma
 */
public class RunContentBuilder extends LogConsoleManagerBase {
  @NonNls private static final String JAVA_RUNNER = "JavaRunner";

  private final ProgramRunner myRunner;
  private final ArrayList<AnAction> myRunnerActions = new ArrayList<AnAction>();
  private ExecutionResult myExecutionResult;

  private final LogFilesManager myManager;

  private RunnerLayoutUi myUi;
  private final Executor myExecutor;

  /**
   * @deprecated use {@link #RunContentBuilder(ProgramRunner, com.intellij.execution.ExecutionResult, ExecutionEnvironment)}
   */
  public RunContentBuilder(@NotNull Project project,
                           ProgramRunner runner,
                           Executor executor,
                           ExecutionResult executionResult,
                           @NotNull ExecutionEnvironment environment) {
    this(runner, executionResult, environment);
  }

  public RunContentBuilder(ProgramRunner runner,
                           ExecutionResult executionResult,
                           @NotNull ExecutionEnvironment environment) {
    super(environment.getProject(), createSearchScope(environment.getProject(), environment.getRunProfile()));
    myRunner = runner;
    myExecutor = environment.getExecutor();
    myManager = new LogFilesManager(environment.getProject(), this, this);
    myExecutionResult = executionResult;
    setEnvironment(environment);
  }

  /**
   * @deprecated use {@link #RunContentBuilder(com.intellij.openapi.project.Project, ProgramRunner, com.intellij.execution.Executor, com.intellij.execution.ExecutionResult, ExecutionEnvironment)}
   */
  public RunContentBuilder(final Project project, final ProgramRunner runner, Executor executor) {
    super(project);
    myRunner = runner;
    myExecutor = executor;
    myManager = new LogFilesManager(project, this, this);
  }

  @NotNull
  public static GlobalSearchScope createSearchScope(Project project, RunProfile runProfile) {
    Module[] modules = null;
    if (runProfile instanceof ModuleRunProfile) {
      modules = ((ModuleRunProfile)runProfile).getModules();
    }
    if (modules == null || modules.length == 0) {
      return GlobalSearchScope.allScope(project);
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(modules[0], true);
      for (int idx = 1; idx < modules.length; idx++) {
        Module module = modules[idx];
        scope = scope.uniteWith(GlobalSearchScope.moduleRuntimeScope(module, true));
      }
      return scope;
    }
  }

  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  @Deprecated
  public void setExecutionResult(final ExecutionResult executionResult) {
    myExecutionResult = executionResult;
  }

  @Override
  public void setEnvironment(@NotNull final ExecutionEnvironment env) {
    super.setEnvironment(env);
    final RunProfile profile = env.getRunProfile();
    if (profile instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)profile);
    }
  }

  public void addAction(@NotNull final AnAction action) {
    myRunnerActions.add(action);
  }

  private RunContentDescriptor createDescriptor() {
    if (myExecutionResult == null) {
      throw new IllegalStateException("Missing ExecutionResult");
    }

    ExecutionEnvironment environment = getEnvironment();
    if (environment == null) {
      throw new IllegalStateException("Missing ExecutionEnvironment");
    }

    final RunProfile profile = environment.getRunProfile();

    final ExecutionConsole console = myExecutionResult.getExecutionConsole();
    String runnerType = JAVA_RUNNER;
    if (console instanceof ExecutionConsoleEx) {
      final String id = ((ExecutionConsoleEx)console).getExecutionConsoleId();
      if (id != null) {
        runnerType = JAVA_RUNNER + "." + id;
      }
    }
    myUi = RunnerLayoutUi.Factory.getInstance(getProject()).create(runnerType, myExecutor.getId(), profile.getName(), this);
    myUi.getOptions().setMoveToGridActionEnabled(false).setMinimizeActionEnabled(false);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new MyRunContentDescriptor(profile, myExecutionResult, myUi.getComponent(), this);
    }

    if (console != null) {
      if (console instanceof ExecutionConsoleEx) {
        ((ExecutionConsoleEx)console).buildUi(myUi);
      }
      else {
        buildConsoleUiDefault(myUi, console);
      }
      if (profile instanceof RunConfigurationBase) {
        myManager.initLogConsoles((RunConfigurationBase)profile, myExecutionResult.getProcessHandler());
        OutputFileUtil.attachDumpListener((RunConfigurationBase)profile, myExecutionResult.getProcessHandler(), console);
      }
    }
    MyRunContentDescriptor contentDescriptor = new MyRunContentDescriptor(profile, myExecutionResult, myUi.getComponent(), this);
    myUi.getOptions().setLeftToolbar(createActionToolbar(contentDescriptor, myUi.getComponent()), ActionPlaces.UNKNOWN);

    if (profile instanceof RunConfigurationBase) {
      if (console instanceof ObservableConsoleView && !ApplicationManager.getApplication().isUnitTestMode()) {
        ((ObservableConsoleView)console).addChangeListener(new ConsoleToFrontListener((RunConfigurationBase)profile,
                                                                                      getProject(),
                                                                                      myExecutor,
                                                                                      contentDescriptor,
                                                                                      myUi),
                                                           this);
      }
    }

    return contentDescriptor;
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
      AnAction[] actions = ((ConsoleView)console).createConsoleActions();
      for (AnAction goaction: actions) {
        consoleActions.add(goaction);
      }
    }

    consoleContent.setActions(consoleActions, ActionPlaces.UNKNOWN, console.getComponent());
  }

  private ActionGroup createActionToolbar(final RunContentDescriptor contentDescriptor, final JComponent component) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();

    final RestartAction restartAction = new RestartAction(myExecutor, myRunner, contentDescriptor, getEnvironment());
    restartAction.registerShortcut(component);
    actionGroup.add(restartAction);
    contentDescriptor.setRestarter(new Runnable() {
      @Override
      public void run() {
        restartAction.restart();
      }
    });

    if (myExecutionResult instanceof DefaultExecutionResult) {
      final AnAction[] actions = ((DefaultExecutionResult)myExecutionResult).getRestartActions();
      if (actions != null) {
        actionGroup.addAll(actions);
        if (actions.length > 0) {
          actionGroup.addSeparator();
        }
      }
    }

    final AnAction stopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
    actionGroup.add(stopAction);
    if (myExecutionResult instanceof DefaultExecutionResult) {
      actionGroup.addAll(((DefaultExecutionResult)myExecutionResult).getAdditionalStopActions());
    }

    actionGroup.addAll(myExecutionResult.getActions());

    for (final AnAction anAction : myRunnerActions) {
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
    actionGroup.add(new CloseAction(myExecutor, contentDescriptor, getProject()));
    final String helpId = contentDescriptor.getHelpId();
    actionGroup.add(new ContextHelpAction(helpId != null ? helpId : myExecutor.getHelpId()));
    return actionGroup;
  }

  @Override
  public ProcessHandler getProcessHandler() {
    return myExecutionResult.getProcessHandler();
  }

  /**
   * @param reuseContent see {@link RunContentDescriptor#myContent}
   */
  public RunContentDescriptor showRunContent(final RunContentDescriptor reuseContent) {
    final RunContentDescriptor descriptor = createDescriptor();
    if(reuseContent != null) descriptor.setAttachedContent(reuseContent.getAttachedContent());
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

  private static class MyRunContentDescriptor extends RunContentDescriptor {
    private final Disposable myAdditionalDisposable;

    public MyRunContentDescriptor(final RunProfile profile, final ExecutionResult executionResult, final JComponent component, @NotNull Disposable additionalDisposable) {
      super(executionResult.getExecutionConsole(), executionResult.getProcessHandler(), component, profile.getName(), profile.getIcon());
      myAdditionalDisposable = additionalDisposable;
    }

    @Override
    public void dispose() {
      Disposer.dispose(myAdditionalDisposable);
      super.dispose();
    }
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
