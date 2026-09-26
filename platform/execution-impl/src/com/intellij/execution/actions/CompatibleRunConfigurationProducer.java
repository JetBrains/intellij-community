// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class CompatibleRunConfigurationProducer<T extends RunConfiguration> extends RunConfigurationProducer<T> {
  protected CompatibleRunConfigurationProducer() {
    super(true);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull T configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    if (!isContextCompatible(context)) {
      return false;
    }
    return setupConfigurationFromCompatibleContext(configuration, context, sourceElement);
  }

  protected abstract boolean setupConfigurationFromCompatibleContext(@NotNull T configuration,
                                                                     @NotNull ConfigurationContext context,
                                                                     @NotNull Ref<PsiElement> sourceElement);

  @Override
  public final boolean isConfigurationFromContext(@NotNull T configuration, @NotNull ConfigurationContext context) {
    if (!isContextCompatible(context)) {
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
