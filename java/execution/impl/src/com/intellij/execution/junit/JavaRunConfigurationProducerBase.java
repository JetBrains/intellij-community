package com.intellij.execution.junit;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class JavaRunConfigurationProducerBase<T extends RunConfiguration> extends RunConfigurationProducer<T> {
  protected JavaRunConfigurationProducerBase(ConfigurationFactory configurationFactory) {
    super(configurationFactory);
  }

  protected JavaRunConfigurationProducerBase(ConfigurationType configurationType) {
    super(configurationType);
  }

  protected boolean setupConfigurationModule(@Nullable ConfigurationContext context, ModuleBasedConfiguration configuration) {
    if (context != null) {
      final RunnerAndConfigurationSettings template = context.getRunManager().getConfigurationTemplate(getConfigurationFactory());
      final Module contextModule = context.getModule();
      final Module predefinedModule = ((ModuleBasedConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
      if (predefinedModule != null) {
        configuration.setModule(predefinedModule);
        return true;
      }
      final Module module = findModule(configuration, contextModule);
      if (module != null) {
        configuration.setModule(module);
        return true;
      }
    }
    return false;
  }

  protected Module findModule(ModuleBasedConfiguration configuration, Module contextModule) {
    if (configuration.getConfigurationModule().getModule() == null && contextModule != null) {
      return contextModule;
    }
    return null;
  }
}
