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

package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.SuggestUsingRunDashBoardUtil.promptUserToUseRunDashboard;

public class RunContextAction extends BaseRunConfigurationAction {
  private final Executor myExecutor;

  public RunContextAction(@NotNull final Executor executor) {
    super(ExecutionBundle.message("perform.action.with.context.configuration.action.name", executor.getStartActionText()), null,
          executor.getIcon());
    myExecutor = executor;
  }

  @Override
  protected void perform(final ConfigurationContext context) {
    RunnerAndConfigurationSettings configuration = context.findExisting();
    final RunManagerEx runManager = (RunManagerEx)context.getRunManager();
    if (configuration == null) {
      configuration = context.getConfiguration();
      if (configuration == null) {
        return;
      }
      runManager.setTemporaryConfiguration(configuration);
    }
    if (Registry.is("select.run.configuration.from.context")) {
      runManager.setSelectedConfiguration(configuration);
    }

    ExecutionUtil.runConfiguration(configuration, myExecutor);
  }

  @Override
  protected boolean isEnabledFor(RunConfiguration configuration) {
    return getRunner(configuration) != null;
  }

  @Nullable
  private ProgramRunner getRunner(final RunConfiguration configuration) {
    return RunnerRegistry.getInstance().getRunner(myExecutor.getId(), configuration);
  }

  @Override
  protected void updatePresentation(final Presentation presentation, @NotNull final String actionText, final ConfigurationContext context) {
    presentation.setText(myExecutor.getStartActionText(actionText), true);

    Pair<Boolean, Boolean> b = isEnabledAndVisible(context);

    presentation.setEnabled(b.first);
    presentation.setVisible(b.second);
  }

  private Pair<Boolean, Boolean> isEnabledAndVisible(ConfigurationContext context) {
    RunnerAndConfigurationSettings configuration = context.findExisting();
    if (configuration == null) {
      configuration = context.getConfiguration();
    }

    ProgramRunner runner = configuration == null ? null : getRunner(configuration.getConfiguration());
    if (runner == null) {
      return Pair.create(false, false);
    }
    return Pair.create(!ExecutorRegistry.getInstance().isStarting(context.getProject(), myExecutor.getId(), runner.getRunnerId()), true);
  }

  @NotNull
  @Override
  protected List<AnAction> createChildActions(@NotNull ConfigurationContext context,
                                              @NotNull List<ConfigurationFromContext> configurations) {
    final List<AnAction> childActions = new ArrayList<>(super.createChildActions(context, configurations));
    boolean isMultipleConfigurationsFromAlternativeLocations =
      configurations.size() > 1 && configurations.get(0).isFromAlternativeLocation();
    boolean isRunAction = myExecutor.getId().equals(DefaultRunExecutor.EXECUTOR_ID);
    if (isMultipleConfigurationsFromAlternativeLocations && isRunAction) {
      childActions.add(runAllConfigurationsAction(context, configurations));
    }

    return childActions;
  }

  @NotNull
  private AnAction runAllConfigurationsAction(@NotNull ConfigurationContext context, @NotNull List<ConfigurationFromContext> configurationsFromContext) {
    return new AnAction(
      "Run all",
      "Run all configurations available in this context",
      LayeredIcon.create(AllIcons.Nodes.Folder, AllIcons.Nodes.RunnableMark)
    ) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        long groupId = ExecutionEnvironment.getNextUnusedExecutionId();

        List<ConfigurationType> types = ContainerUtil.map(configurationsFromContext, context1 -> context1.getConfiguration().getType());
        promptUserToUseRunDashboard(context.getProject(), types);

        for (ConfigurationFromContext configuration : configurationsFromContext) {
          ExecutionUtil.runConfiguration(configuration.getConfigurationSettings(), myExecutor, groupId);
        }
      }
    };
  }

}
