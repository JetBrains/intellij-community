/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.CloseAction;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
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
  private final ProgramRunner myRunner;
  private final Project myProject;
  private final ArrayList<Disposable> myDisposeables = new ArrayList<Disposable>();
  private final ArrayList<AnAction> myRunnerActions = new ArrayList<AnAction>();
  private Icon myRerunIcon = DEFAULT_RERUN_ICON;
  private boolean myReuseProhibited = false;
  private ExecutionResult myExecutionResult;
  private RunProfile myRunProfile;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  private final LogFilesManager myManager;

  private RunnerLayoutUi myUi;
  private Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private Executor myExecutor;

  public RunContentBuilder(final Project project, final ProgramRunner runner, Executor executor) {
    myProject = project;
    myRunner = runner;
    myExecutor = executor;
    myManager = new LogFilesManager(project, this);
  }

  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  public void setExecutionResult(final ExecutionResult executionResult) {
    myExecutionResult = executionResult;
  }

  public void setRunProfile(final RunProfile runProfile,
                            RunnerSettings runnerSettings,
                            ConfigurationPerRunnerSettings configurationSettings) {
    myRunProfile = runProfile;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    if (runProfile instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)runProfile);
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
    if (myRunProfile == null) {
      throw new IllegalStateException("Missing RunProfile");
    }

    myUi = RunnerLayoutUi.Factory.getInstance(myProject).create("JavaRunner", myExecutor.getId(), myRunProfile.getName(), this);
    myUi.setMoveToGridActionEnabled(false).setMinimizeActionEnabled(false);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new MyRunContentDescriptor(myRunProfile, myExecutionResult, myReuseProhibited, myUi.getComponent(), this);
    }

    final ExecutionConsole console = myExecutionResult.getExecutionConsole();
    if (console != null) {
      DefaultActionGroup consoleActions = new DefaultActionGroup();
      if (console instanceof ConsoleView) {
        AnAction[] actions = ((ConsoleView)console).createUpDownStacktraceActions();
        for (AnAction goaction: actions) {
          consoleActions.add(goaction);
        }
      }

      final Content consoleContent = myUi.createContent("Console", console.getComponent(), "Console",
                                                        IconLoader.getIcon("/debugger/console.png"),
                                                        console.getPreferredFocusableComponent());

      consoleContent.setActions(consoleActions, ActionPlaces.UNKNOWN, console.getComponent());
      myUi.addContent(consoleContent, 0, RunnerLayoutUi.PlaceInGrid.bottom, false);
      if (myRunProfile instanceof RunConfigurationBase){
        myManager.initLogConsoles((RunConfigurationBase)myRunProfile, myExecutionResult.getProcessHandler());
      }
    }

    MyRunContentDescriptor contentDescriptor = new MyRunContentDescriptor(myRunProfile, myExecutionResult, myReuseProhibited, myUi.getComponent(), this);

    myUi.setLeftToolbar(createActionToolbar(contentDescriptor, myUi.getComponent()), ActionPlaces.UNKNOWN);

    return contentDescriptor;
  }

  public void addLogConsole(final String name, final String path, final long skippedContent) {
    final LogConsole log = new LogConsole(myProject, new File(path), skippedContent, name, false){
      public boolean isActive() {
        final Content content = myUi.findContent(path);
        return content != null && content.isSelected();
      }
    };
    log.attachStopLogConsoleTrackingListener(myExecutionResult.getProcessHandler());
    addAdditionalTabComponent(log, path);

    Disposer.register(this, log);

    myUi.addListener(new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        log.stateChanged(new ChangeEvent(myUi));
      }
    }, log);
  }

  public void removeLogConsole(final String path) {
    final Content content = myUi.findContent(path);
    if (content != null) {
      final LogConsole log = (LogConsole)content.getComponent();
      removeAdditionalTabComponent(log);
    }
  }

  private ActionGroup createActionToolbar(final RunContentDescriptor contentDescriptor, final JComponent component) {
    final RestartAction action = new RestartAction(myExecutor, myRunner, myRunProfile, getProcessHandler(), myRerunIcon, contentDescriptor, myRunnerSettings, myConfigurationSettings);
    action.registerShortcut(component);
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(action);

    final AnAction[] profileActions = myExecutionResult.getActions();
    for (final AnAction profileAction : profileActions) {
      actionGroup.add(profileAction);
    }

    for (final AnAction anAction : myRunnerActions) {
      if (anAction != null) {
        actionGroup.add(anAction);
      }
      else {
        actionGroup.addSeparator();
      }
    }

    final AnAction stopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
    actionGroup.add(stopAction);
    actionGroup.addSeparator();
    actionGroup.add(myUi.getLayoutActions());
    actionGroup.addSeparator();
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
    component.dispose();
    myDisposeables.remove(component);
    final Content content = myAdditionalContent.remove(component);
    myUi.removeContent(content, true);
  }

  private class MyRunContentDescriptor extends RunContentDescriptor {
    private final boolean myReuseProhibited;
    private final Disposable[] myAdditionalDisposables;

    public MyRunContentDescriptor(final RunProfile profile, final ExecutionResult executionResult, final boolean reuseProhibited, final JComponent component, final Disposable... additionalDisposables) {
      super(executionResult.getExecutionConsole(), executionResult.getProcessHandler(), component, profile.getName());
      myReuseProhibited = reuseProhibited;
      myAdditionalDisposables = additionalDisposables;
    }

    public boolean isContentReuseProhibited() {
      return myReuseProhibited;
    }

    public void dispose() {
      for (final Disposable disposable : myAdditionalDisposables) {
        Disposer.dispose(disposable);
      }
      myManager.unregisterFileMatcher();
      super.dispose();
    }
  }
}
