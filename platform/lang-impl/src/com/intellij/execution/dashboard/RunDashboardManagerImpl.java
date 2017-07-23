/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.tree.DashboardGrouper;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.*;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author konstantin.aleev
 */
@State(
  name = "RunDashboard",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class RunDashboardManagerImpl implements RunDashboardManager, PersistentStateComponent<RunDashboardManagerImpl.State> {
  @NotNull private final Project myProject;
  @NotNull private final ContentManager myContentManager;
  @NotNull private final List<DashboardGrouper> myGroupers;
  private boolean myShowConfigurations = true;

  private RunDashboardContent myDashboardContent;
  private Content myToolWindowContent;

  public RunDashboardManagerImpl(@NotNull final Project project) {
    myProject = project;

    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    ContentUI contentUI = new PanelContentUI();
    myContentManager = contentFactory.createContentManager(contentUI, false, project);

    myGroupers = Arrays.stream(DashboardGroupingRule.EP_NAME.getExtensions())
      .sorted(DashboardGroupingRule.PRIORITY_COMPARATOR)
      .map(DashboardGrouper::new)
      .collect(Collectors.toList());

    if (isDashboardEnabled()) {
      initToolWindowListeners();
    }
  }

  private static boolean isDashboardEnabled() {
    return Registry.is("ide.run.dashboard") && RunDashboardContributor.EP_NAME.getExtensions().length > 0;
  }

  private void initToolWindowListeners() {
    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);

    connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        updateDashboardIfNeeded(settings);
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        updateDashboardIfNeeded(settings);
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        updateDashboardIfNeeded(settings);
      }
    });
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, final @NotNull ProcessHandler handler) {
        updateToolWindowContent();
        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        updateToolWindowContent();
        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }
    });
    connection.subscribe(RunDashboardManager.DASHBOARD_TOPIC, new DashboardListener() {
      @Override
      public void contentChanged(boolean withStructure) {
        updateDashboard(withStructure);
      }
    });
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
      }

      @Override
      public void exitDumbMode() {
        updateDashboard(false);
      }
    });
    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        updateToolWindowContent();
        updateDashboard(true);
      }
    });
  }

  @Override
  public ContentManager getDashboardContentManager() {
    return myContentManager;
  }

  @Override
  public String getToolWindowId() {
    return ToolWindowId.RUN_DASHBOARD;
  }

  @Override
  public Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun; // TODO [konstantin.aleev] provide new icon
  }

  @Override
  public boolean isToolWindowAvailable() {
    return isDashboardEnabled() && hasContent();
  }

  @Override
  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    myDashboardContent = new RunDashboardContent(myProject, myContentManager, myGroupers);
    myToolWindowContent = new RunDashboardTabbedContent(myContentManager, myDashboardContent, null, false);
    myToolWindowContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    Disposer.register(myToolWindowContent, myDashboardContent);
    Disposer.register(myToolWindowContent, () -> myDashboardContent = null);
    toolWindow.getContentManager().addContent(myToolWindowContent);
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.COMBO);
    toolWindow.setContentUiType(ToolWindowContentUiType.COMBO, null);
  }

  @Override
  public List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> getRunConfigurations() {
    List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> result = new ArrayList<>();

    List<RunnerAndConfigurationSettings> configurations = RunManager.getInstance(myProject).getAllSettings().stream()
      .filter(runConfiguration -> RunDashboardContributor.getContributor(runConfiguration.getType()) != null)
      .collect(Collectors.toList());

    //noinspection ConstantConditions ???
    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
    configurations.forEach(configurationSettings -> {
      List<RunContentDescriptor> descriptors = filterByContent(executionManager.getDescriptors(
          settings -> Comparing.equal(settings.getConfiguration(), configurationSettings.getConfiguration())));
      RunDashboardContributor contributor = RunDashboardContributor.getContributor(configurationSettings.getType());
      if (descriptors.isEmpty() && contributor != null &&  contributor.isShowInDashboard(configurationSettings.getConfiguration())) {
        result.add(Pair.create(configurationSettings, null));
      }
      else {
        descriptors.forEach(descriptor -> result.add(Pair.create(configurationSettings, descriptor)));
      }
    });

    // It is possible that run configuration was deleted, but there is running content descriptor for such run configuration.
    // It should be shown in he dashboard tree.
    List<RunConfiguration> storedConfigurations = configurations.stream().map(RunnerAndConfigurationSettings::getConfiguration)
      .collect(Collectors.toList());
    List<RunContentDescriptor> notStoredDescriptors = filterByContent(executionManager.getRunningDescriptors(settings ->
      RunDashboardContributor.getContributor(settings.getType()) != null && !storedConfigurations.contains(settings.getConfiguration())));
    notStoredDescriptors.forEach(descriptor -> {
      Set<RunnerAndConfigurationSettings> settings = executionManager.getConfigurations(descriptor);
      settings.forEach(setting -> result.add(Pair.create(setting, descriptor)));
    });

    return result;
  }

  private List<RunContentDescriptor> filterByContent(List<RunContentDescriptor> descriptors) {
    return descriptors.stream()
      .filter(descriptor -> {
        Content content = descriptor.getAttachedContent();
        return content != null && content.getManager() == myContentManager;
      })
      .collect(Collectors.toList());
  }

  @Override
  public boolean isShowConfigurations() {
    return myShowConfigurations;
  }

  @Override
  public void setShowConfigurations(boolean value) {
    myShowConfigurations = value;
    updateToolWindowContent();
  }

  private void updateDashboardIfNeeded(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null && RunDashboardContributor.getContributor(settings.getType()) != null) {
      updateDashboard(true);
    }
  }

  private void updateDashboard(final boolean withStructure) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      if (withStructure) {
        boolean available = hasContent();
        ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolWindowId());
        if (toolWindow == null) {
          if (available) {
            createToolWindow();
          }
          return;
        }

        boolean doShow = !toolWindow.isAvailable() && available;
        toolWindow.setAvailable(available, null);
        if (doShow) {
          toolWindow.show(null);
        }
      }

      if (myDashboardContent != null) {
        myDashboardContent.updateContent(withStructure);
      }
    });
  }

  private void createToolWindow() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(getToolWindowId(), false, ToolWindowAnchor.BOTTOM,
                                                                 myProject, true);
    toolWindow.setIcon(getToolWindowIcon());
    createToolWindowContent(toolWindow);
  }

  private boolean hasContent() {
    return !getRunConfigurations().isEmpty();
  }

  private void updateToolWindowContent() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      if (myToolWindowContent == null) {
        return;
      }

      String tabName = null;
      Icon tabIcon = null;
      if (!myShowConfigurations) {
        Content content = myContentManager.getSelectedContent();
        if (content != null) {
          tabName = content.getTabName();
          tabIcon = content.getIcon();
        }
      }
      myToolWindowContent.setDisplayName(tabName);
      myToolWindowContent.setIcon(tabIcon);

      ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowId());
      if (toolWindow instanceof ToolWindowImpl) {
        ToolWindowContentUi contentUi = ((ToolWindowImpl)toolWindow).getContentUI();
        contentUi.revalidate();
        contentUi.repaint();
      }
    });
  }

  @Nullable
  @Override
  public State getState() {
    State state = new State();
    state.ruleStates = myGroupers.stream()
      .filter(grouper -> !grouper.getRule().isAlwaysEnabled())
      .map(grouper -> new RuleState(grouper.getRule().getName(), grouper.isEnabled()))
      .collect(Collectors.toList());
    return state;
  }

  @Override
  public void loadState(State state) {
    state.ruleStates.forEach(ruleState -> {
      for (DashboardGrouper grouper : myGroupers) {
        if (grouper.getRule().getName().equals(ruleState.name) && !grouper.getRule().isAlwaysEnabled()) {
          grouper.setEnabled(ruleState.enabled);
          return;
        }
      }
    });
  }

  static class State {
    public List<RuleState> ruleStates = new ArrayList<>();
  }

  private static class RuleState {
    public String name;
    public boolean enabled = true;

    @SuppressWarnings("UnusedDeclaration")
    RuleState() {
    }

    RuleState(String name, boolean enabled) {
      this.name = name;
      this.enabled = enabled;
    }
  }

  private static class RunDashboardTabbedContent extends ContentImpl implements TabbedContent {
    private ContentManager myDashboardContentManager;
    private String myTitlePrefix = "Running Configurations:";

    RunDashboardTabbedContent(ContentManager dashboardContentManager, JComponent component, String displayName, boolean isPinnable) {
      super(component, displayName, isPinnable);
      myDashboardContentManager = dashboardContentManager;
    }

    @Override
    public void addContent(@NotNull JComponent content, @NotNull String name, boolean selectTab) {
    }

    @Override
    public void removeContent(@NotNull JComponent content) {
    }

    @Override
    public void selectContent(int index) {
      Content content = myDashboardContentManager.getContent(index);
      if (content != null) {
        myDashboardContentManager.setSelectedContent(content);
      }
    }

    @Override
    public List<Pair<String, JComponent>> getTabs() {
      return Arrays.stream(myDashboardContentManager.getContents())
        .map(content -> Pair.create(content.getDisplayName(), content.getComponent())).collect(Collectors.toList());
    }

    @Override
    public boolean hasMultipleTabs() {
      return myDashboardContentManager.getContents().length > 1;
    }

    @Override
    public String getTitlePrefix() {
      return myTitlePrefix;
    }

    @Override
    public void setTitlePrefix(String titlePrefix) {
      myTitlePrefix = titlePrefix;
    }

    @Override
    public void split() {
    }

    @Override
    public void dispose() {
      myDashboardContentManager = null;
      super.dispose();
    }
  }
}
