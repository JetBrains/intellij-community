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

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsoleImpl;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dyoma
 */
public class RunContentBuilder implements LogConsoleManager, Disposable  {
  private static final Icon DEFAULT_RERUN_ICON = IconLoader.getIcon("/actions/refreshUsages.png");
  @NonNls private static final String JAVA_RUNNER = "JavaRunner";
  
  private final ProgramRunner myRunner;
  private final Project myProject;
  private final ArrayList<Disposable> myDisposeables = new ArrayList<Disposable>();
  private final ArrayList<AnAction> myRunnerActions = new ArrayList<AnAction>();
  private final Icon myRerunIcon = DEFAULT_RERUN_ICON;
  private final boolean myReuseProhibited = false;
  private ExecutionResult myExecutionResult;

  private final LogFilesManager myManager;

  private RunnerLayoutUi myUi;
  private final Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private final Executor myExecutor;
  private ExecutionEnvironment myEnvironment;

  public RunContentBuilder(final Project project, final ProgramRunner runner, Executor executor) {
    myProject = project;
    myRunner = runner;
    myExecutor = executor;
    myManager = new LogFilesManager(project, this, this);
  }

  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  public void setExecutionResult(final ExecutionResult executionResult) {
    myExecutionResult = executionResult;
  }

  public void setEnvironment(@NotNull final ExecutionEnvironment env) {
    myEnvironment = env;
    final RunProfile profile = env.getRunProfile();
    if (profile instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)profile);
    }
  }

  public void addAction(@NotNull final AnAction action) {
    myRunnerActions.add(action);
  }

  public void dispose() {
    for (Disposable disposable : myDisposeables) {
      disposable.dispose();
    }
  }

  private RunContentDescriptor createDescriptor() {
    if (myExecutionResult == null) {
      throw new IllegalStateException("Missing ExecutionResult");
    }

    if (myEnvironment == null) {
      throw new IllegalStateException("Missing ExecutionEnvironment");
    }

    final RunProfile profile = myEnvironment.getRunProfile();

    final ExecutionConsole console = myExecutionResult.getExecutionConsole();
    String runnerType = JAVA_RUNNER;
    if (console instanceof ExecutionConsoleEx) {
      final String id = ((ExecutionConsoleEx)console).getExecutionConsoleId();
      if (id != null) {
        runnerType = JAVA_RUNNER + "." + id;
      }
    }
    myUi = RunnerLayoutUi.Factory.getInstance(myProject).create(runnerType, myExecutor.getId(), profile.getName(), this);
    myUi.getOptions().setMoveToGridActionEnabled(false).setMinimizeActionEnabled(false);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new MyRunContentDescriptor(profile, myExecutionResult, myReuseProhibited, myUi.getComponent(), this);
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
      }
    }
    MyRunContentDescriptor contentDescriptor = new MyRunContentDescriptor(profile, myExecutionResult, myReuseProhibited, myUi.getComponent(), this);
    myUi.getOptions().setLeftToolbar(createActionToolbar(contentDescriptor, myUi.getComponent()), ActionPlaces.UNKNOWN);

    return contentDescriptor;
  }

  public static void buildConsoleUiDefault(RunnerLayoutUi ui, final ExecutionConsole console) {
    DefaultActionGroup consoleActions = new DefaultActionGroup();
    if (console instanceof ConsoleView) {
      AnAction[] actions = ((ConsoleView)console).createConsoleActions();
      for (AnAction goaction: actions) {
        consoleActions.add(goaction);
      }
    }

    final Content consoleContent = ui.createContent("Console", console.getComponent(), "Console",
                                                      IconLoader.getIcon("/debugger/console.png"),
                                                      console.getPreferredFocusableComponent());

    consoleContent.setActions(consoleActions, ActionPlaces.UNKNOWN, console.getComponent());
    ui.addContent(consoleContent, 0, PlaceInGrid.bottom, false);
  }

  public void addLogConsole(final String name, final String path, final long skippedContent) {
    final LogConsoleImpl log = new LogConsoleImpl(myProject, new File(path), skippedContent, name, false){
      public boolean isActive() {
        final Content content = myUi.findContent(path);
        return content != null && content.isSelected();
      }
    };
    if (myEnvironment.getRunProfile() instanceof RunConfigurationBase) {
      ((RunConfigurationBase) myEnvironment.getRunProfile()).customizeLogConsole(log);
    }
    log.attachStopLogConsoleTrackingListener(myExecutionResult.getProcessHandler());
    addAdditionalTabComponent(log, path);

    myUi.addListener(new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        log.stateChanged(new ChangeEvent(myUi));
      }
    }, log);
  }

  public void removeLogConsole(final String path) {
    final Content content = myUi.findContent(path);
    if (content != null) {
      final LogConsoleImpl log = (LogConsoleImpl)content.getComponent();
      removeAdditionalTabComponent(log);
    }
  }

  private ActionGroup createActionToolbar(final RunContentDescriptor contentDescriptor, final JComponent component) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();

    final RestartAction restartAction = new RestartAction(myExecutor, myRunner, getProcessHandler(), myRerunIcon, contentDescriptor, myEnvironment);
    restartAction.registerShortcut(component);
    actionGroup.add(restartAction);
    contentDescriptor.setRestarter(new Runnable() {
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
    actionGroup.add(new CloseAction(myExecutor, contentDescriptor, myProject));
    actionGroup.add(new ContextHelpAction(myExecutor.getHelpId()));
    return actionGroup;
  }

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

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent, final String id) {
    final Content content = myUi.createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(),
                                               IconLoader.getIcon("/debugger/console.png"), tabComponent.getPreferredFocusableComponent());



    myUi.addContent(content);
    myAdditionalContent.put(tabComponent, content);

    myDisposeables.add(new Disposable(){
      public void dispose() {
        if (!myUi.isDisposed()) {
          removeAdditionalTabComponent(tabComponent);
        }
      }
    });
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    Disposer.dispose(component);
    myDisposeables.remove(component);
    final Content content = myAdditionalContent.remove(component);
    myUi.removeContent(content, true);
  }

  private static class MyRunContentDescriptor extends RunContentDescriptor {
    private final boolean myReuseProhibited;
    private final Disposable myAdditionalDisposable;

    public MyRunContentDescriptor(final RunProfile profile, final ExecutionResult executionResult, final boolean reuseProhibited, final JComponent component, @NotNull Disposable additionalDisposable) {
      super(executionResult.getExecutionConsole(), executionResult.getProcessHandler(), component, profile.getName(), profile.getIcon());
      myReuseProhibited = reuseProhibited;
      myAdditionalDisposable = additionalDisposable;
    }

    public boolean isContentReuseProhibited() {
      return myReuseProhibited;
    }

    public void dispose() {
      Disposer.dispose(myAdditionalDisposable);
      super.dispose();
    }
  }
}
