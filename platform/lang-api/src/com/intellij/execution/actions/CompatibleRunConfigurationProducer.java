/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class CompatibleRunConfigurationProducer<T extends RunConfiguration> extends RunConfigurationProducer<T> {

  protected CompatibleRunConfigurationProducer(@NotNull ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(T configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    if (configuration == null || context == null || sourceElement == null || !isContextCompatible(context)) {
      return false;
    }
    return setupConfigurationFromCompatibleContext(configuration, context, sourceElement);
  }

  protected abstract boolean setupConfigurationFromCompatibleContext(@NotNull T configuration,
                                                                     @NotNull ConfigurationContext context,
                                                                     @NotNull Ref<PsiElement> sourceElement);

  @Override
  public final boolean isConfigurationFromContext(T configuration, ConfigurationContext context) {
    if (configuration == null || context == null || !isContextCompatible(context)) {
      return false;
    }
    return isConfigurationFromCompatibleContext(configuration, context);
  }

  protected abstract boolean isConfigurationFromCompatibleContext(@NotNull T configuration, @NotNull ConfigurationContext context);

  protected boolean isContextCompatible(@NotNull ConfigurationContext context) {
    ConfigurationType type = getConfigurationType();
    return context.isCompatibleWithOriginalRunConfiguration(type);
  }
}
