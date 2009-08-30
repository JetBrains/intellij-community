/*
 * Class RemoteConfigurationFactory
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RemoteConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/remote.png");

  /**reflection*/
  public RemoteConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new RemoteConfiguration("", project, this);
      }

    };
  }

  public String getDisplayName() {
    return ExecutionBundle.message("remote.debug.configuration.display.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("remote.debug.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @NotNull
  public String getId() {
    return "Remote";
  }

  @Nullable
  public static RemoteConfigurationType getInstance() {
    return ContainerUtil.findInstance(Extensions.getExtensions(CONFIGURATION_TYPE_EP), RemoteConfigurationType.class);
  }

}