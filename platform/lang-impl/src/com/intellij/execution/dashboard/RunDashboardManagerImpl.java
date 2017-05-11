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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentUI;
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

  private RunDashboardContent myDashboardContent;

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
    MessageBusConnection connection = myProject.getMessageBus().connect();

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
        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
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
    ContentManager contentManager = toolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(myDashboardContent, null, false);
    Disposer.register(content, myDashboardContent);
    Disposer.register(content, () -> myDashboardContent = null);
    toolWindow.getContentManager().addContent(content);
  }

  @Override
  public List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> getRunConfigurations() {
    List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> result = new ArrayList<>();

    List<RunnerAndConfigurationSettings> configurations = RunManager.getInstance(myProject).getAllSettings().stream()
      .filter(runConfiguration -> RunDashboardContributor.isShowInDashboard(runConfiguration.getType()))
      .collect(Collectors.toList());

    //noinspection ConstantConditions ???
    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
    configurations.forEach(configurationSettings -> {
      List<RunContentDescriptor> descriptors = executionManager.getDescriptors(
          settings -> Comparing.equal(settings.getConfiguration(), configurationSettings.getConfiguration()));
      if (descriptors.isEmpty()) {
        result.add(Pair.create(configurationSettings, null));
      } else {
        descriptors.forEach(descriptor -> result.add(Pair.create(configurationSettings, descriptor)));
      }
    });

    // It is possible that run configuration was deleted, but there is running content descriptor for such run configuration.
    // It should be shown in he dashboard tree.
    List<RunConfiguration> storedConfigurations = configurations.stream().map(RunnerAndConfigurationSettings::getConfiguration)
      .collect(Collectors.toList());
    List<RunContentDescriptor> notStoredDescriptors = executionManager.getRunningDescriptors(settings -> {
      RunConfiguration configuration = settings.getConfiguration();
      return RunDashboardContributor.isShowInDashboard(settings.getType()) && !storedConfigurations.contains(configuration);
    });
    notStoredDescriptors.forEach(descriptor -> {
      Set<RunnerAndConfigurationSettings> settings = executionManager.getConfigurations(descriptor);
      settings.forEach(setting -> result.add(Pair.create(setting, descriptor)));
    });

    return result;
  }

  private void updateDashboardIfNeeded(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null && RunDashboardContributor.isShowInDashboard(settings.getType())) {
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
    public RuleState() {
    }

    public RuleState(String name, boolean enabled) {
      this.name = name;
      this.enabled = enabled;
    }
  }
}
