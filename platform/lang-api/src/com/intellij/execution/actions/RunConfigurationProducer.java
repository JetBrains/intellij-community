/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class RunConfigurationProducer<T extends RunConfiguration> {
  public static ExtensionPointName<RunConfigurationProducer> EP_NAME = ExtensionPointName.create("com.intellij.runConfigurationProducer");
  private final ConfigurationFactory myConfigurationFactory;

  protected RunConfigurationProducer(final ConfigurationFactory configurationFactory) {
    myConfigurationFactory = configurationFactory;
  }

  protected RunConfigurationProducer(final ConfigurationType configurationType) {
    myConfigurationFactory = configurationType.getConfigurationFactories()[0];
  }

  public ConfigurationType getConfigurationType() {
    return myConfigurationFactory.getType();
  }

  @Nullable
  public ConfigurationFromContext createConfigurationFromContext(ConfigurationContext context) {
    final RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    if (!setupConfigurationFromContext((T)settings.getConfiguration(), context)) {
      return null;
    }
    return new ConfigurationFromContextImpl(settings, context.getPsiLocation());
  }

  protected abstract boolean setupConfigurationFromContext(T configuration, ConfigurationContext context);

  public abstract boolean isConfigurationFromContext(T configuration, ConfigurationContext context);

  public ConfigurationFromContext findOrCreateConfigurationFromContext(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) {
      return null;
    }

    ConfigurationFromContext fromContext = createConfigurationFromContext(context);
    if (fromContext != null) {
      final PsiElement psiElement = fromContext.getSourceElement();
      final Location<PsiElement> _location = PsiLocation.fromPsiElement(psiElement, location.getModule());
      if (_location != null) {
        // replace with existing configuration if any
        final RunManager runManager = RunManager.getInstance(context.getProject());
        final ConfigurationType type = fromContext.getConfigurationType();
        final RunnerAndConfigurationSettings[] configurations = runManager.getConfigurationSettings(type);
        final RunnerAndConfigurationSettings configuration = findExistingConfiguration(context);
        if (configuration != null) {
          fromContext.setConfigurationSettings(configuration);
        } else {
          final ArrayList<String> currentNames = new ArrayList<String>();
          for (RunnerAndConfigurationSettings configurationSettings : configurations) {
            currentNames.add(configurationSettings.getName());
          }
          fromContext.getConfiguration().setName(RunManager.suggestUniqueName(fromContext.getConfiguration().getName(), currentNames));
        }
      }
    }

    return fromContext;
  }

  @Nullable
  public RunnerAndConfigurationSettings findExistingConfiguration(ConfigurationContext context) {
    final RunManager runManager = RunManager.getInstance(context.getProject());
    final RunnerAndConfigurationSettings[] configurations = runManager.getConfigurationSettings(myConfigurationFactory.getType());
    for (RunnerAndConfigurationSettings configurationSettings : configurations) {
      if (isConfigurationFromContext((T) configurationSettings.getConfiguration(), context)) {
        return configurationSettings;
      }
    }
    return null;
  }

  protected RunnerAndConfigurationSettings cloneTemplateConfiguration(@NotNull final ConfigurationContext context) {
    final RunConfiguration original = context.getOriginalConfiguration(myConfigurationFactory.getType());
    if (original != null) {
      return RunManager.getInstance(context.getProject()).createConfiguration(original.clone(), myConfigurationFactory);
    }
    return RunManager.getInstance(context.getProject()).createRunConfiguration("", myConfigurationFactory);
  }
}
