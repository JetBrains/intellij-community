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
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.execution.ui.layout.impl.GridImpl;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.MoreActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.impl.content.SingleContentSupplier;
import com.intellij.psi.search.ExecutionSearchScopes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class RunTab implements DataProvider, Disposable {
  /**
   * Takes out an action of 'More' group and adds it on the toolbar.
   * <p>
   * This option has to be set into {@link AnAction#getTemplatePresentation()}.
   * Works only if new UI is enabled.
   */
  @ApiStatus.Experimental
  public static final Key<PreferredPlace> PREFERRED_PLACE = Key.create("RunTab.preferredActionPlace");

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
         ExecutionSearchScopes.executionScope(environment.getProject(), environment.getRunProfile()),
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
    else if (ExecutionDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
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
    private final Map<TabInfo, Content> myTabInfoContentMap = new LinkedHashMap<>();
    private boolean myMoveToolbar = false;

    private final ActionGroup layoutActionGroup = new ActionGroup(
      ExecutionBundle.messagePointer("runner.content.tooltip.layout.settings"), () -> "", AllIcons.Debugger.RestoreLayout
    ) {
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
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
      @Override
      public boolean isDumbAware() {
        return true;
      }
    };

    public RunTabSupplier(@Nullable ActionGroup group) {
      myActionGroup = group;
      layoutActionGroup.setPopup(true);
      layoutActionGroup.getTemplatePresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
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
      return List.of(layoutActionGroup);
    }

    @Override
    public void init(@Nullable ActionToolbar mainToolbar, @Nullable ActionToolbar contentToolbar) {
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

    @Override
    public void close(@NotNull TabInfo tab) {
      GridImpl grid = (GridImpl)tab.getComponent();
      ViewContext context = ViewContext.CONTEXT_KEY.getData(grid);
      Content[] content = ViewContext.CONTENT_KEY.getData(grid);
      if (context == null || content == null || content.length == 0) {
        SingleContentSupplier.super.close(tab);
        return;
      }
      context.getContentManager().removeContent(content[0], context.isToDisposeRemovedContent());
    }

    @Override
    public void addSubContent(@NotNull TabInfo tabInfo, @NotNull Content content) {
      myTabInfoContentMap.put(tabInfo, content);
    }

    @NotNull
    @Override
    public Collection<Content> getSubContents() {
      return myTabInfoContentMap.values();
    }

    public boolean isMoveToolbar() {
      return myMoveToolbar;
    }

    public void setMoveToolbar(boolean moveToolbar) {
      myMoveToolbar = moveToolbar;
    }
  }

  /**
   * <p>A special action group that can hold Run actions.</p>
   * <p>
   *   If {@link RunTabSupplier#isMoveToolbar()} returns <code>true</code>
   *   then this group prepends {@link RunTabSupplier#getToolbarActions()}.
   * </p><p>
   *   Also, merges last {@link MoreActionGroup} groups into one.
   * </p>
   */
  public static final class ToolbarActionGroup extends DefaultActionGroup {

    private final MoreActionGroup myMoreActionGroup = new MoreActionGroup();

    public ToolbarActionGroup(ActionGroup group) {
      addAll(group);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      AnAction[] children = super.getChildren(e);
      if (e != null) {
        ToolWindow window = e.getData(PlatformDataKeys.TOOL_WINDOW);
        SingleContentSupplier data = e.getData(SingleContentSupplier.KEY);
        if (data instanceof RunTabSupplier && window != null) {
          boolean isMoveToolbar = ((RunTabSupplier)data).isMoveToolbar();
          if (!isMoveToolbar) return children;
          ContentManager manager = window.getContentManager();
          if (manager.getContentCount() > 1) {
            ActionGroup actions = data.getToolbarActions();
            if (actions != null) return merge(actions.getChildren(e), children);
          }
        }
      }
      return children;
    }

    private AnAction @NotNull [] merge(AnAction @NotNull [] head, AnAction @NotNull [] tail) {
      var result = new ArrayList<AnAction>(head.length + tail.length);
      result.addAll(Arrays.asList(head));
      myMoreActionGroup.removeAll();
      if (ArrayUtil.getLastElement(head) instanceof MoreActionGroup) {
        result.remove(result.size() - 1);
      }
      result.add(Separator.create());
      result.addAll(Arrays.asList(tail));
      if (ArrayUtil.getLastElement(tail) instanceof MoreActionGroup) {
        myMoreActionGroup.addAll((ActionGroup) ArrayUtil.getLastElement(tail));
        result.remove(result.size() - 1);
      }
      if (ArrayUtil.getLastElement(head) instanceof MoreActionGroup) {
        myMoreActionGroup.add(Separator.create());
        myMoreActionGroup.addAll((ActionGroup) ArrayUtil.getLastElement(head));
      }
      result.add(myMoreActionGroup);
      return result.toArray(EMPTY_ARRAY);
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }
  }
}
