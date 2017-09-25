package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Basic run configuration type for external system tasks.
 *
 * @author Denis Zhdanov
 * @since 23.05.13 17:43
 */
public abstract class AbstractExternalSystemTaskConfigurationType implements ConfigurationType {

  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final ConfigurationFactory[] myFactories = new ConfigurationFactory[1];

  @NotNull private final NotNullLazyValue<Icon> myIcon = new NotNullLazyValue<Icon>() {
    @NotNull
    @Override
    protected Icon compute() {
      ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(myExternalSystemId);
      Icon result = null;
      if (manager instanceof ExternalSystemUiAware) {
        result = ((ExternalSystemUiAware)manager).getProjectIcon();
      }
      return result == null ? DefaultExternalSystemUiAware.INSTANCE.getTaskIcon() : result;
    }
  };

  protected AbstractExternalSystemTaskConfigurationType(@NotNull final ProjectSystemId externalSystemId) {
    myExternalSystemId = externalSystemId;
    myFactories[0] = new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return doCreateConfiguration(myExternalSystemId, project, this, "");
      }
    };
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @NotNull
  public ConfigurationFactory getFactory() {
    return myFactories[0];
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  protected ExternalSystemRunConfiguration doCreateConfiguration(@NotNull ProjectSystemId externalSystemId,
                                                                 @NotNull Project project,
                                                                 @NotNull ConfigurationFactory factory,
                                                                 @NotNull String name)
  {
    return new ExternalSystemRunConfiguration(externalSystemId, project, factory, name);
  }

  @Override
  public String getDisplayName() {
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

  @NotNull
  @Override
  public String getId() {
    return myExternalSystemId.getReadableName() + "RunConfiguration";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return myFactories;
  }

  @NotNull
  public static String generateName(@NotNull Project project, @NotNull ExternalSystemTaskExecutionSettings settings) {
    return generateName(
      project, settings.getExternalSystemId(), settings.getExternalProjectPath(), settings.getTaskNames(), settings.getExecutionName()
    );
  }

  @NotNull
  public static String generateName(@NotNull Project project, @NotNull ExternalTaskPojo task, @NotNull ProjectSystemId externalSystemId) {
    return generateName(project, externalSystemId, task.getLinkedExternalProjectPath(), Collections.singletonList(task.getName()));
  }

  @NotNull
  public static String generateName(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @Nullable String externalProjectPath,
                                    @NotNull List<String> taskNames) {
    return generateName(project, externalSystemId, externalProjectPath, taskNames, null);
  }

  @NotNull
  public static String generateName(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @Nullable String externalProjectPath,
                                    @NotNull List<String> taskNames,
                                    @Nullable String executionName) {
    return generateName(project, externalSystemId, externalProjectPath, taskNames, executionName, " [", "]");
  }

  @NotNull
  public static String generateName(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @Nullable String externalProjectPath,
                                    @NotNull List<String> taskNames,
                                    @Nullable String executionName,
                                    @NotNull String tasksPrefix,
                                    @NotNull String tasksPostfix) {

    String rootProjectPath = null;
    if (externalProjectPath != null) {
      final ExternalProjectInfo projectInfo = ExternalSystemUtil.getExternalProjectInfo(project, externalSystemId, externalProjectPath);
      if (projectInfo != null) {
        rootProjectPath = projectInfo.getExternalProjectPath();
      }
    }

    StringBuilder buffer = new StringBuilder();
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
    } else {
      buffer.append(externalProjectPath);
    }

    buffer.append(tasksPrefix);
    if (!StringUtil.isEmpty(executionName)) {
      buffer.append(executionName);
    }
    else if (!taskNames.isEmpty()) {
      for (String taskName : taskNames) {
        buffer.append(taskName).append(' ');
      }
      buffer.setLength(buffer.length() - 1);
    }
    buffer.append(tasksPostfix);

    return buffer.toString();
  }
}
