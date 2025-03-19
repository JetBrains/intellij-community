// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunConfigurationStartHistory;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.SuggestUsingRunDashBoardUtil.promptUserToUseRunDashboard;

public class RunContextAction extends BaseRunConfigurationAction {
  private final Executor myExecutor;

  public RunContextAction(@NotNull Executor executor) {
    super(ExecutionBundle.messagePointer("perform.action.with.context.configuration.action.name", executor.getStartActionText()),
          Presentation.NULL_STRING,
          executor::getIcon);
    myExecutor = executor;
  }

  @Override
  protected void perform(@NotNull RunnerAndConfigurationSettings configuration,
                         @NotNull ConfigurationContext context) {
    RunManagerEx runManager = (RunManagerEx)context.getRunManager();
    DataContext dataContext = context.getDefaultDataContext();
    ReadAction
      .nonBlocking(() -> findExisting(context))
      .finishOnUiThread(ModalityState.nonModal(), existingConfiguration -> {
        if (configuration != existingConfiguration) {
          RunConfigurationOptionUsagesCollector.logAddNew(context.getProject(), configuration.getType().getId(), context.getPlace());
          runManager.setTemporaryConfiguration(configuration);
          perform(runManager, configuration, dataContext);
        }
        else {
          perform(runManager, configuration, dataContext);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void perform(RunManagerEx runManager,
                       RunnerAndConfigurationSettings configuration, 
                       DataContext dataContext) {
    if (runManager.shouldSetRunConfigurationFromContext()) {
      runManager.setSelectedConfiguration(configuration);
      RunConfigurationStartHistory.getInstance(configuration.getConfiguration().getProject()).register(configuration);
    }

    if (LOG.isDebugEnabled()) {
      String configurationClass = configuration.getConfiguration().getClass().getName();
      LOG.debug(String.format("Execute run configuration: %s", configurationClass));
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    ExecutionUtil.doRunConfiguration(configuration, myExecutor, null, null, dataContext);
  }

  @Override
  protected boolean isEnabledFor(@NotNull RunConfiguration configuration) {
    return getRunner(configuration) != null;
  }

  private @Nullable ProgramRunner<?> getRunner(@NotNull RunConfiguration configuration) {
    return ProgramRunner.getRunner(myExecutor.getId(), configuration);
  }

  @Override
  protected void updatePresentation(@NotNull Presentation presentation, final @NotNull String actionText, final ConfigurationContext context) {
    presentation.setText(myExecutor.getStartActionText(actionText), true);

    Pair<Boolean, Boolean> b = isEnabledAndVisible(context);

    presentation.setEnabled(b.first);
    presentation.setVisible(b.second);
  }

  private Pair<Boolean, Boolean> isEnabledAndVisible(@NotNull ConfigurationContext context) {
    RunnerAndConfigurationSettings configuration = findExisting(context);
    if (configuration == null) {
      configuration = context.getConfiguration();
    }

    ProgramRunner<?> runner = configuration == null ? null : getRunner(configuration.getConfiguration());
    if (runner == null) {
      return Pair.create(false, false);
    }

    Project project = context.getProject();
    return Pair.create(!ExecutionManager.getInstance(project).isStarting(
      configuration.getUniqueID(), myExecutor.getId(), runner.getRunnerId()), true);
  }

  @Override
  protected @NotNull List<AnAction> createChildActions(@NotNull ConfigurationContext context,
                                                       @NotNull List<? extends ConfigurationFromContext> configurations) {
    final List<AnAction> childActions = new ArrayList<>(super.createChildActions(context, configurations));
    boolean isMultipleConfigurationsFromAlternativeLocations =
      configurations.size() > 1 && configurations.get(0).isFromAlternativeLocation();
    boolean isRunAction = myExecutor.getId().equals(DefaultRunExecutor.EXECUTOR_ID);
    if (isMultipleConfigurationsFromAlternativeLocations && isRunAction) {
      childActions.add(runAllConfigurationsAction(context, configurations));
    }

    return childActions;
  }

  private @NotNull AnAction runAllConfigurationsAction(@NotNull ConfigurationContext context,
                                                       @NotNull List<? extends ConfigurationFromContext> configurationsFromContext) {
    return new AnAction(
      CommonBundle.message("action.text.run.all"),
      ExecutionBundle.message("run.all.configurations.available.in.this.context"),
      AllIcons.RunConfigurations.Compound
    ) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        long groupId = ExecutionEnvironment.getNextUnusedExecutionId();

        List<ConfigurationType> types = ContainerUtil.map(configurationsFromContext, context1 -> context1.getConfiguration().getType());
        promptUserToUseRunDashboard(context.getProject(), types);

        for (ConfigurationFromContext configuration : configurationsFromContext) {
          ExecutionUtil.runConfiguration(configuration.getConfigurationSettings(), myExecutor, groupId);
        }
      }
    };
  }

  public Executor getExecutor() {
    return myExecutor;
  }
}
