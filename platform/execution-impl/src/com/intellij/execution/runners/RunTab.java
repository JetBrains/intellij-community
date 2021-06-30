// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.diagnostic.logging.LogConsoleManagerBase;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.diagnostic.logging.OutputFileUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.GridImpl;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.content.SingleContentSupplier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

public abstract class RunTab implements DataProvider, Disposable {
  @NotNull
  protected final RunnerLayoutUi myUi;
  private LogFilesManager myManager;
  protected RunContentDescriptor myRunContentDescriptor;

  protected ExecutionEnvironment myEnvironment;
  protected final Project myProject;
  protected final GlobalSearchScope mySearchScope;

  private LogConsoleManagerBase logConsoleManager;

  protected RunTab(@NotNull ExecutionEnvironment environment, @NotNull String runnerType) {
    this(environment.getProject(),
         GlobalSearchScopes.executionScope(environment.getProject(), environment.getRunProfile()),
         runnerType,
         environment.getExecutor().getId(),
         environment.getRunProfile().getName());

    myEnvironment = environment;
  }

  @Override
  public void dispose() {
    myRunContentDescriptor = null;
    myEnvironment = null;
    logConsoleManager = null;
  }

  protected RunTab(@NotNull Project project, @NotNull GlobalSearchScope searchScope, @NotNull String runnerType, @NotNull String runnerTitle, @NotNull String sessionName) {
    myProject = project;
    mySearchScope = searchScope;

    myUi = RunnerLayoutUi.Factory.getInstance(project).create(runnerType, runnerTitle, sessionName, this);
    myUi.getContentManager().addDataProvider(this);
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (LangDataKeys.RUN_PROFILE.is(dataId)) {
      return myEnvironment == null ? null : myEnvironment.getRunProfile();
    }
    else if (LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
      return myEnvironment;
    }
    else if (LangDataKeys.RUN_CONTENT_DESCRIPTOR.is(dataId)) {
      return myRunContentDescriptor;
    } else if (SingleContentSupplier.KEY.is(dataId)) {
      return getSupplier();
    }
    return null;
  }

  @Nullable
  protected SingleContentSupplier getSupplier() {
    return null;
  }

  @NotNull
  public LogConsoleManagerBase getLogConsoleManager() {
    if (logConsoleManager == null) {
      logConsoleManager = new LogConsoleManagerBase(myProject, mySearchScope) {
        @Override
        protected Icon getDefaultIcon() {
          return AllIcons.Debugger.Console;
        }

        @Override
        protected RunnerLayoutUi getUi() {
          return myUi;
        }

        @Override
        public ProcessHandler getProcessHandler() {
          return myRunContentDescriptor == null ? null : myRunContentDescriptor.getProcessHandler();
        }
      };
    }
    return logConsoleManager;
  }

  protected final void initLogConsoles(@NotNull RunProfile runConfiguration, @NotNull RunContentDescriptor contentDescriptor, @Nullable ExecutionConsole console) {
    ProcessHandler processHandler = contentDescriptor.getProcessHandler();
    if (runConfiguration instanceof RunConfigurationBase) {
      RunConfigurationBase configuration = (RunConfigurationBase)runConfiguration;
      if (myManager == null) {
        myManager = new LogFilesManager(myProject, getLogConsoleManager(), contentDescriptor);
      }
      myManager.addLogConsoles(configuration, processHandler);
      if (processHandler != null) {
        OutputFileUtil.attachDumpListener(configuration, processHandler, console);
      }
    }
  }

  /**
   * Default implementation of {@link SingleContentSupplier}.
   *
   * Isn't used directly by {@link RunTab}, but can be used by inheritors.
   */
  protected class RunTabSupplier implements SingleContentSupplier {

    @Nullable
    private final ActionGroup myActionGroup;

    public RunTabSupplier(@Nullable ActionGroup group) {
      myActionGroup = group;
    }

    @NotNull
    @Override
    public JBTabs getTabs() {
      RunnerContentUi contentUi = RunnerContentUi.KEY.getData((DataProvider)myUi);
      return Objects.requireNonNull(contentUi).getTabs();
    }

    @Nullable
    @Override
    public ActionGroup getToolbarActions() {
      return myActionGroup;
    }

    @NotNull
    @Override
    public List<AnAction> getContentActions() {
      var layout = new ActionGroup(ExecutionBundle.messagePointer("runner.content.tooltip.layout.settings"), () -> "", AllIcons.Debugger.RestoreLayout) {
          @Override
          public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            RunnerContentUi contentUi = RunnerContentUi.KEY.getData((DataProvider)myUi);
            return Objects.requireNonNull(contentUi).getViewActions();
          }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabledAndVisible(getChildren(null).length > 0);
        }

        @Override
        public boolean isDumbAware() {
          return true;
        }
      };
      layout.setPopup(true);
      layout.getTemplatePresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);

      return List.of(layout);
    }

    @Override
    public void init(@Nullable ActionToolbar toolbar) {
      JBTabs tabs = getTabs();
      if (tabs instanceof JBTabsEx) {
        ((JBTabsEx)tabs).setHideTopPanel(true);
      }
    }

    @Override
    public void reset() {
      JBTabs tabs = getTabs();
      if (tabs instanceof JBTabsEx) {
        ((JBTabsEx)tabs).setHideTopPanel(false);
      }
    }

    @Override
    public boolean isClosable(@NotNull TabInfo tab) {
      List<Content> gridContents = ((GridImpl)tab.getComponent()).getContents();
      return gridContents.size() > 0 && gridContents.get(0).isCloseable();
    }
  }
}
