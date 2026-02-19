// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps a {@link RuntimeConfigurationProducer} in a {@link ConfigurationFromContext}.
 */
public class ConfigurationFromContextWrapper extends ConfigurationFromContext {
  private final RuntimeConfigurationProducer myProducer;

  public ConfigurationFromContextWrapper(RuntimeConfigurationProducer producer) {
    myProducer = producer;
  }

  @Override
  public void onFirstRun(ConfigurationContext context, Runnable startRunnable) {
    myProducer.perform(context, startRunnable);
  }

  @Override
  public @NotNull RunnerAndConfigurationSettings getConfigurationSettings() {
    return myProducer.getConfiguration();
  }

  @Override
  public void setConfigurationSettings(RunnerAndConfigurationSettings configurationSettings) {
    myProducer.setConfiguration(configurationSettings);
  }

  @Override
  public @NotNull PsiElement getSourceElement() {
    return myProducer.getSourceElement();
  }

  @Override
  public boolean isPreferredTo(ConfigurationFromContext other) {
    return other instanceof ConfigurationFromContextWrapper &&
           myProducer.compareTo(((ConfigurationFromContextWrapper) other).myProducer) < 0;
  }

  @Override
  public boolean shouldReplace(ConfigurationFromContext other) {
    return other instanceof ConfigurationFromContextWrapper &&
           myProducer.compareTo(((ConfigurationFromContextWrapper) other).myProducer) > 0;
  }
}
