/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class ProgramRunnerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ProgramRunnerUtil");

  private ProgramRunnerUtil() {
  }

  @Nullable
  public static ProgramRunner getRunner(@NotNull final String executorId, final RunnerAndConfigurationSettings configuration) {
    return configuration == null ? null : RunnerRegistry.getInstance().getRunner(executorId, configuration.getConfiguration());
  }

  public static void executeConfiguration(@NotNull final Project project,
                                          @Nullable final DataContext context,
                                          @NotNull final RunnerAndConfigurationSettings configuration,
                                          @NotNull final Executor executor,
                                          @NotNull final ExecutionTarget target,
                                          @Nullable RunContentDescriptor contentToReuse,
                                          final boolean showSettings) {
    ProgramRunner runner = getRunner(executor.getId(), configuration);
    if (runner == null) {
      LOG.error("Runner MUST not be null! Cannot find runner for " +
                executor.getId() +
                " and " +
                configuration.getConfiguration().getFactory().getName());
      return;
    }
    executeConfiguration(project, context, configuration, executor, target, contentToReuse, showSettings, runner, null, true);
  }

  public static void executeConfiguration(Project project,
                                          DataContext context,
                                          @Nullable RunnerAndConfigurationSettings configuration,
                                          Executor executor,
                                          ExecutionTarget target,
                                          RunContentDescriptor contentToReuse,
                                          boolean showSettings,
                                          @NotNull ProgramRunner runner,
                                          @Nullable RunProfile runProfile,
                                          boolean assignNewId) {
    if (ExecutorRegistry.getInstance().isStarting(project, executor.getId(), runner.getRunnerId())) {
      return;
    }

    if (configuration != null && !ExecutionTargetManager.canRun(configuration, target)) {
      ExecutionUtil.handleExecutionError(
        project, executor.getToolWindowId(), configuration.getConfiguration(),
        new ExecutionException(StringUtil.escapeXml("Cannot run '" + configuration.getName() + "' on '" + target.getDisplayName() + "'")));
      return;
    }

    if (configuration != null &&
        (!RunManagerImpl.canRunConfiguration(configuration, executor) || (showSettings && configuration.isEditBeforeRun()))) {
      if (!RunDialog.editConfiguration(project, configuration, "Edit configuration", executor)) {
        return;
      }

      while (!RunManagerImpl.canRunConfiguration(configuration, executor)) {
        if (0 == Messages
          .showYesNoDialog(project, "Configuration is still incorrect. Do you want to edit it again?", "Change Configuration Settings",
                           "Edit", "Continue Anyway", Messages.getErrorIcon())) {
          if (!RunDialog.editConfiguration(project, configuration, "Edit configuration", executor)) {
            return;
          }
        }
        else {
          break;
        }
      }
    }

    final ConfigurationType configurationType = configuration != null ? configuration.getType() : null;
    if (configurationType != null) {
      UsageTrigger.trigger("execute." + ConvertUsagesUtil.ensureProperKey(configurationType.getId()) + "." + executor.getId());
    }

    try {
      ExecutionEnvironmentBuilder builder =
        new ExecutionEnvironmentBuilder(project, executor);
      if (configuration != null) {
        builder.setRunnerAndSettings(runner, configuration);
      }
      else {
        builder.setRunnerId(runner.getRunnerId());
      }
      builder.setTarget(target).setContentToReuse(contentToReuse).setDataContext(context);
      if (assignNewId) {
        builder.assignNewId();
      }
      if (runProfile != null) {
        builder.setRunProfile(runProfile);
      }
      runner.execute(builder.build());
    }
    catch (ExecutionException e) {
      String name = configuration != null ? configuration.getName() : null;
      if (name == null && runProfile != null) name = runProfile.getName();
      if (name == null && contentToReuse != null) name = contentToReuse.getDisplayName();
      if (name == null) name = "<Unknown>";
      ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), name, e);
    }
  }


  public static void executeConfiguration(@NotNull Project project,
                                          @NotNull RunnerAndConfigurationSettings configuration,
                                          @NotNull Executor executor) {
    executeConfiguration(project, null, configuration, executor, ExecutionTargetManager.getActiveTarget(project), null, true);
  }

  public static Icon getConfigurationIcon(final RunnerAndConfigurationSettings settings,
                                          final boolean invalid) {
    RunConfiguration configuration = settings.getConfiguration();
    ConfigurationFactory factory = settings.getFactory();
    Icon icon =  factory != null ? factory.getIcon(configuration) : null;
    if (icon == null) icon = AllIcons.RunConfigurations.Unknown;

    final Icon configurationIcon = settings.isTemporary() ? IconLoader.getTransparentIcon(icon, 0.3f) : icon;
    if (invalid) {
      return LayeredIcon.create(configurationIcon, AllIcons.RunConfigurations.InvalidConfigurationLayer);
    }

    return configurationIcon;
  }

  public static String shortenName(final String name, final int toBeAdded) {
    if (name == null) return "";
    final int symbols = Math.max(10, 20 - toBeAdded);
    if (name.length() < symbols) return name;
    else return name.substring(0, symbols) + "...";
  }
}
