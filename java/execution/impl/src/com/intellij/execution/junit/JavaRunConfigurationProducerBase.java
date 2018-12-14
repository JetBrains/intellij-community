// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class JavaRunConfigurationProducerBase<T extends ModuleBasedConfiguration> extends RunConfigurationProducer<T> {
  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  protected JavaRunConfigurationProducerBase(ConfigurationFactory configurationFactory) {
    super(configurationFactory);
  }

  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  protected JavaRunConfigurationProducerBase(@NotNull ConfigurationType configurationType) {
    super(configurationType.getConfigurationFactories()[0]);
  }

  protected JavaRunConfigurationProducerBase() {
    super(true);
  }

  protected boolean setupConfigurationModule(@Nullable ConfigurationContext context, T configuration) {
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

  protected Module findModule(T configuration, Module contextModule) {
    if (configuration.getConfigurationModule().getModule() == null && contextModule != null) {
      return contextModule;
    }
    return null;
  }

  protected TestSearchScope setupPackageConfiguration(ConfigurationContext context,
                                                      T configuration,
                                                      TestSearchScope scope) {
    if (scope != TestSearchScope.WHOLE_PROJECT) {
      if (!setupConfigurationModule(context, configuration)) {
        return TestSearchScope.WHOLE_PROJECT;
      }
    }
    return scope;
  }

  @Nullable
  @Override
  public ConfigurationFromContext createConfigurationFromContext(ConfigurationContext context) {
    ConfigurationFromContext fromContext = super.createConfigurationFromContext(context);
    if (fromContext != null) {
      JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration((RunConfigurationBase)fromContext.getConfiguration(),
                                                                                    context.getLocation());
    }
    return fromContext;
  }
}
