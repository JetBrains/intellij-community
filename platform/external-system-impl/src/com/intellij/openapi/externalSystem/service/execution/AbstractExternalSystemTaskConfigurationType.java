package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * // TODO den add doc
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
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return doCreateConfiguration(myExternalSystemId, project, this, "");
      }
    };
  }

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
}
