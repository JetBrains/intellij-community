/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.CloseAction;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.actions.CommonActionsFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * @author dyoma
 */
public class RunContentBuilder implements LogConsoleManager {
  private static final Icon DEFAULT_RERUN_ICON = IconLoader.getIcon("/actions/refreshUsages.png");
  private final JavaProgramRunner myRunner;
  private final Project myProject;
  private final ArrayList<Disposable> myDisposeables = new ArrayList<Disposable>();
  private final ArrayList<AnAction> myRunnerActions = new ArrayList<AnAction>();
  private Icon myRerunIcon = DEFAULT_RERUN_ICON;
  private boolean myReuseProhibited = false;
  private ExecutionResult myExecutionResult;
  private JComponent myComponent;
  private RunProfile myRunProfile;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  private final LogFilesManager myManager;
  private JPanel myContentPanel;

  public RunContentBuilder(final Project project, final JavaProgramRunner runner) {
    myProject = project;
    myRunner = runner;
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

  private RunContentDescriptor createDescriptor() {
    if (myExecutionResult == null) {
      throw new IllegalStateException("Missing ExecutionResult");
    }
    if (myRunProfile == null) {
      throw new IllegalStateException("Missing RunProfile");
    }

    myContentPanel = new JPanel(new BorderLayout(2, 0));
    myContentPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    final Disposable disposable = new Disposable() {
      public void dispose() {
        for (Disposable disposable : myDisposeables) {
          disposable.dispose();
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new MyRunContentDescriptor(myRunProfile, myExecutionResult, myReuseProhibited, myContentPanel, disposable);
    }
    if (myComponent == null) {
      final ExecutionConsole console = myExecutionResult.getExecutionConsole();
      if (console != null) {
        if (myRunProfile instanceof JUnitConfiguration){
          myComponent = console.getComponent();
        } else {
          if (myComponent == null){
            myComponent = console.getComponent();
          }
          if (myRunProfile instanceof RunConfigurationBase){            
            myManager.initLogConsoles((RunConfigurationBase)myRunProfile, myExecutionResult.getProcessHandler());
          }
        }
      }
    }
    MyRunContentDescriptor contentDescriptor = new MyRunContentDescriptor(myRunProfile, myExecutionResult, myReuseProhibited, myContentPanel, disposable);
    if (myComponent != null) {
      myContentPanel.add(myComponent, BorderLayout.CENTER);
    }
    myContentPanel.add(createActionToolbar(contentDescriptor, myContentPanel), BorderLayout.WEST);
    return contentDescriptor;
  }

  public void addLogConsole(final String name, final String path, final long skippedContent) {
    final LogConsole log = new LogConsole(myProject, new File(path), skippedContent, name){
      public boolean isActive() {
        return myComponent instanceof JTabbedPane && ((JTabbedPane)myComponent).getSelectedComponent() == this;
      }
    };
    log.attachStopLogConsoleTrackingListener(myExecutionResult.getProcessHandler());
    addAdditionalTabComponent(log);
    ((JTabbedPane)myComponent).addChangeListener(log);
  }

  public void removeLogConsole(final String path) {
    LogConsole componentToRemove = null;
    for (Disposable tabComponent : myDisposeables) {
      if (tabComponent instanceof LogConsole) {
        final LogConsole console = (LogConsole)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          componentToRemove = console;
          break;
        }
      }
    }
    if (componentToRemove != null) {
      ((JTabbedPane)myComponent).removeChangeListener(componentToRemove);
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  private JComponent createActionToolbar(final RunContentDescriptor contentDescriptor, final JComponent component) {
    final RestartAction action = new RestartAction(myRunner, myRunProfile, getProcessHandler(), myRerunIcon, contentDescriptor, myRunnerSettings, myConfigurationSettings);
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

    ExecutionConsole console = myExecutionResult.getExecutionConsole();
    if (console instanceof ConsoleView) {
      AnAction[] actions = ((ConsoleView)console).createUpDownStacktraceActions();
      for (AnAction goaction: actions) {
        actionGroup.add(goaction);
      }
    }
    final AnAction stopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
    actionGroup.add(stopAction);
    actionGroup.add(new CloseAction(myRunner, contentDescriptor, myProject));
    actionGroup.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(myRunner.getInfo().getHelpId()));
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false).getComponent();
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

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent) {
    myDisposeables.add(new Disposable(){
      public void dispose() {
        if (tabComponent instanceof LogConsole) {
          ((JTabbedPane)myComponent).removeChangeListener((LogConsole)tabComponent);
        }
        removeAdditionalTabComponent(tabComponent);
      }
    });
    if (! (myComponent instanceof JTabbedPane)) {
      JComponent component = myComponent;
      myComponent = new JTabbedPane();
      if (myContentPanel != null) {
        myContentPanel.remove(component);
        myContentPanel.add(myComponent, BorderLayout.CENTER);
      }
      ((JTabbedPane)myComponent).addTab(ExecutionBundle.message("run.configuration.console.tab"), component);
    }
    ((JTabbedPane)myComponent).addTab(tabComponent.getTabTitle(), null, tabComponent.getComponent(), tabComponent.getTooltip());
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    component.dispose();
    myDisposeables.remove(component);
    myComponent.remove(component);
    if (((JTabbedPane)myComponent).getTabCount() == 1) {
      myComponent = (JComponent)((JTabbedPane)myComponent).getComponentAt(0);
    }
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
        disposable.dispose();
      }
      myManager.unregisterFileMatcher();
      super.dispose();
    }
  }
}
