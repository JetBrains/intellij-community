// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.*;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.compound.SettingsAndEffectiveTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runToolbar.RunToolbarProcessData;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.content.Content;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.execution.dashboard.actions.RunDashboardActionUtils.getLeafTargets;

/**
 * @author konstantin.aleev
 */
public abstract class ExecutorAction extends DumbAwareAction {
  private static final Key<List<Integer>> RUNNABLE_LEAVES_KEY =
    Key.create("RUNNABLE_LEAVES_KEY");

  protected ExecutorAction() {
  }

  protected ExecutorAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      update(e, false);
      return;
    }
    List<RunDashboardRunConfigurationNode> targetNodes = getLeafTargets(e).toList();
    boolean running = ContainerUtil.find(targetNodes, node -> {
      Content content = node.getContent();
      return content != null && !RunContentManagerImpl.isTerminated(content);
    }) != null;
    update(e, running);
    List<Integer> runnableLeaves = getRunnableLeaves(targetNodes);
    Presentation presentation = e.getPresentation();
    if (!runnableLeaves.isEmpty()) {
      presentation.putClientProperty(RUNNABLE_LEAVES_KEY, runnableLeaves);
    }
    else {
      presentation.putClientProperty(RUNNABLE_LEAVES_KEY, null);
    }
    presentation.setEnabled(!runnableLeaves.isEmpty());
    presentation.setVisible(!targetNodes.isEmpty());
  }

  private List<Integer> getRunnableLeaves(List<RunDashboardRunConfigurationNode> targetNodes) {
    List<Integer> runnableLeaves = new SmartList<>();
    for (int i = 0; i < targetNodes.size(); i++) {
      RunDashboardRunConfigurationNode node = targetNodes.get(i);
      if (canRun(node)) {
        runnableLeaves.add(i);
      }
    }
    return runnableLeaves;
  }

  private boolean canRun(@NotNull RunDashboardRunConfigurationNode node) {
    ProgressManager.checkCanceled();

    Project project = node.getProject();
    return canRun(node.getConfigurationSettings(),
                  null,
                  DumbService.isDumb(project));
  }

  private boolean canRun(@NotNull RunnerAndConfigurationSettings settings,
                         @Nullable ExecutionTarget target,
                         boolean isDumb) {
    if (isDumb && !settings.getType().isDumbAware()) return false;

    String executorId = getExecutor().getId();
    RunConfiguration configuration = settings.getConfiguration();
    Project project = configuration.getProject();
    if (configuration instanceof CompoundRunConfiguration comp) {
      if (ExecutionTargetManager.getInstance(project).getTargetsFor(comp).isEmpty()) return false;

      List<SettingsAndEffectiveTarget> subConfigurations = comp.getConfigurationsWithEffectiveRunTargets();
      if (subConfigurations.isEmpty()) return false;

      RunManager runManager = RunManager.getInstance(project);
      for (SettingsAndEffectiveTarget subConfiguration : subConfigurations) {
        RunnerAndConfigurationSettings subSettings = runManager.findSettings(subConfiguration.getConfiguration());
        if (subSettings == null || !canRun(subSettings, subConfiguration.getTarget(), isDumb)) {
          return false;
        }
      }
      return true;
    }

    if (!isValid(settings)) return false;

    ProgramRunner<?> runner = ProgramRunner.getRunner(executorId, configuration);
    if (runner == null) return false;

    if (target == null) {
      target = ExecutionTargetManager.getInstance(project).findTarget(configuration);
      if (target == null) return false;
    }
    else if (!ExecutionTargetManager.canRun(configuration, target)) {
      return false;
    }
    return !ExecutionManager.getInstance(project).isStarting(
      settings.getUniqueID(), executorId, runner.getRunnerId());
  }

  private static boolean isValid(RunnerAndConfigurationSettings settings) {
    try {
      settings.checkSettings(null);
      return true;
    }
    catch (RuntimeConfigurationError ex) {
      return false;
    }
    catch (IndexNotReadyException | RuntimeConfigurationException ex) {
      return true;
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    List<RunDashboardRunConfigurationNode> targetNodes = getLeafTargets(e).toList();
    List<Integer> runnableLeaves = e.getPresentation().getClientProperty(RUNNABLE_LEAVES_KEY);
    if (runnableLeaves == null) {
      // We try to recalculate it because in the backend + frontend case update & perform are 2 different
      // requests to backend, and for now this client property won't be saved between them.
      // We won't count leaves twice in case they are empty because in that case update will disable the action.
      runnableLeaves = getRunnableLeaves(targetNodes);
      if (runnableLeaves.isEmpty()) return;
    }

    for (int i: runnableLeaves) {
      if (targetNodes.size() > i) {
        RunDashboardRunConfigurationNode node = targetNodes.get(i);
        run(node.getConfigurationSettings(), node.getDescriptor(), e.getDataContext());
      }
    }
  }

  private void run(RunnerAndConfigurationSettings settings, RunContentDescriptor descriptor, @NotNull DataContext context) {
    runSubProcess(settings, null, descriptor, RunToolbarProcessData.prepareBaseSettingCustomization(settings, environment -> {
      environment.setDataContext(context);
    }));
  }

  private void runSubProcess(RunnerAndConfigurationSettings settings, ExecutionTarget target, RunContentDescriptor descriptor, @Nullable Consumer<ExecutionEnvironment> envCustomization) {
    RunConfiguration configuration = settings.getConfiguration();
    Project project = configuration.getProject();
    RunManager runManager = RunManager.getInstance(project);
    if (configuration instanceof CompoundRunConfiguration) {
      List<SettingsAndEffectiveTarget> subConfigurations =
        ((CompoundRunConfiguration)configuration).getConfigurationsWithEffectiveRunTargets();
      for (SettingsAndEffectiveTarget subConfiguration : subConfigurations) {
        RunnerAndConfigurationSettings subSettings = runManager.findSettings(subConfiguration.getConfiguration());
        if (subSettings != null) {
          runSubProcess(subSettings, subConfiguration.getTarget(), null, envCustomization);
        }
      }
    }
    else {
      if (target == null) {
        target = ExecutionTargetManager.getInstance(project).findTarget(configuration);
        assert target != null : "No target for configuration of type " + configuration.getType().getDisplayName();
      }
      ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();

      ExecutionManager.getInstance(project).restartRunProfile(project, getExecutor(), target, settings, processHandler, RunToolbarProcessData.prepareSuppressMainSlotCustomization(project, envCustomization));
    }
  }

  protected abstract Executor getExecutor();

  protected abstract void update(@NotNull AnActionEvent e, boolean running);
}
