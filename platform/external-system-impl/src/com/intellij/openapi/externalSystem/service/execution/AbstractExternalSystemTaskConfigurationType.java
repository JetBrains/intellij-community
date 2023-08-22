// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.diagnostic.PluginException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Basic run configuration type for external system tasks.
 */
public abstract class AbstractExternalSystemTaskConfigurationType implements ConfigurationType {
  private final ProjectSystemId myExternalSystemId;
  private final ConfigurationFactory[] myFactories = new ConfigurationFactory[1];
  private final NotNullLazyValue<Icon> myIcon;

  protected AbstractExternalSystemTaskConfigurationType(@NotNull ProjectSystemId externalSystemId) {
    myExternalSystemId = externalSystemId;
    myFactories[0] = new ConfigurationFactory(this) {
      @Override
      public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return doCreateConfiguration(myExternalSystemId, project, this, "");
      }

      @Override
      public @NotNull String getId() {
        return getConfigurationFactoryId();
      }

      @Override
      public boolean isEditableInDumbMode() {
        return AbstractExternalSystemTaskConfigurationType.this.isEditableInDumbMode();
      }
    };
    myIcon = NotNullLazyValue.createValue(() -> {
        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(myExternalSystemId);
        Icon result = null;
        if (manager instanceof ExternalSystemUiAware) {
          result = ((ExternalSystemUiAware)manager).getProjectIcon();
        }
        return result == null ? DefaultExternalSystemUiAware.INSTANCE.getTaskIcon() : result;
      });
  }

  protected boolean isEditableInDumbMode() {
    return false;
  }

  /**
   * This method must be overridden and a proper ID must be returned from it (it'll be used as a key in run configuration file).
   */
  protected @NonNls @NotNull String getConfigurationFactoryId() {
    PluginException.reportDeprecatedDefault(
      getClass(), "getConfigurationFactoryId",
      "The default implementation delegates to 'ProjectSystemId::getReadableName' which is supposed to be localized," +
      " but return value of this method must not be localized.");
    return myExternalSystemId.getReadableName();
  }

  public @NotNull ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  public @NotNull ConfigurationFactory getFactory() {
    return myFactories[0];
  }

  protected @NotNull ExternalSystemRunConfiguration doCreateConfiguration(@NotNull ProjectSystemId externalSystemId,
                                                                          @NotNull Project project,
                                                                          @NotNull ConfigurationFactory factory,
                                                                          @NotNull String name) {
    return new ExternalSystemRunConfiguration(externalSystemId, project, factory, name);
  }

  @Override
  public @NotNull String getDisplayName() {
    return myExternalSystemId.getReadableName();
  }

  @Override
  public String getConfigurationTypeDescription() {
    return ExternalSystemBundle.message("run.configuration.description", myExternalSystemId.getReadableName());
  }

  @Override
  public Icon getIcon() {
    return myIcon.getValue();
  }

  @Override
  public @NotNull String getId() {
    return myExternalSystemId.getReadableName() + "RunConfiguration";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return myFactories;
  }

  public static @NotNull @NlsActions.ActionText String generateName(@NotNull Project project, @NotNull ExternalSystemTaskExecutionSettings settings) {
    return generateName(
      project, settings.getExternalSystemId(), settings.getExternalProjectPath(), settings.getTaskNames(), settings.getExecutionName()
    );
  }

  public static @NotNull @NlsActions.ActionText String generateName(@NotNull Project project,
                                                                    @NotNull ProjectSystemId externalSystemId,
                                                                    @Nullable String externalProjectPath,
                                                                    @NotNull List<String> taskNames,
                                                                    @Nullable @Nls String executionName) {
    return generateName(project, externalSystemId, externalProjectPath, taskNames, executionName, " [", "]");
  }

  public static @NotNull @NlsActions.ActionText String generateName(@NotNull Project project,
                                                                    @NotNull ProjectSystemId externalSystemId,
                                                                    @Nullable String externalProjectPath,
                                                                    @NotNull List<String> taskNames,
                                                                    @Nullable @Nls String executionName,
                                                                    @NotNull String tasksPrefix,
                                                                    @NotNull String tasksPostfix) {
    if (!StringUtil.isEmpty(executionName)) {
      return executionName;
    }
    boolean isTasksAbsent = taskNames.isEmpty();
    String rootProjectPath = null;
    if (externalProjectPath != null) {
      final ExternalProjectInfo projectInfo = ExternalSystemUtil.getExternalProjectInfo(project, externalSystemId, externalProjectPath);
      if (projectInfo != null) {
        rootProjectPath = projectInfo.getExternalProjectPath();
      }
    }

    @Nls StringBuilder buffer = new StringBuilder();
    final String projectName;
    if (rootProjectPath == null) {
      projectName = null;
    }
    else {
      final ExternalSystemUiAware uiAware = ExternalSystemUiUtil.getUiAware(externalSystemId);
      projectName = uiAware.getProjectRepresentationName(project, externalProjectPath, rootProjectPath);
    }
    if (!StringUtil.isEmptyOrSpaces(projectName)) {
      buffer.append(projectName);
    }
    else if (!StringUtil.isEmptyOrSpaces(externalProjectPath)) {
      buffer.append(externalProjectPath);
    }

    if (!isTasksAbsent) buffer.append(tasksPrefix);
    if (!StringUtil.isEmpty(executionName)) {
      buffer.append(executionName);
    }
    else {
      if (!isTasksAbsent) {
        for (String taskName : taskNames) {
          buffer.append(taskName).append(' ');
        }
        buffer.setLength(buffer.length() - 1);
      }
    }
    if (!isTasksAbsent) buffer.append(tasksPostfix);

    if (buffer.length() == 0) {
      buffer.append(ExecutionBundle.message("run.configuration.unnamed.name.prefix"));
    }
    return buffer.toString();
  }
}
