// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;


public class ConfigurationFromContextImpl extends ConfigurationFromContext {
  private final RunConfigurationProducer myConfigurationProducer;
  private RunnerAndConfigurationSettings myConfigurationSettings;
  private final PsiElement mySourceElement;

  public ConfigurationFromContextImpl(RunConfigurationProducer producer, RunnerAndConfigurationSettings settings, PsiElement element) {
    myConfigurationProducer = producer;
    myConfigurationSettings = settings;
    mySourceElement = element;
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  @Override
  public void setConfigurationSettings(RunnerAndConfigurationSettings configurationSettings) {
    myConfigurationSettings = configurationSettings;
  }

  @NotNull
  @Override
  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  @Override
  public boolean isPreferredTo(ConfigurationFromContext other) {
    return myConfigurationProducer.isPreferredConfiguration(this, other);
  }

  @Override
  public boolean shouldReplace(ConfigurationFromContext other) {
    return myConfigurationProducer.shouldReplace(this, other);
  }

  @Override
  public boolean isProducedBy(Class<? extends RunConfigurationProducer> producerClass) {
    return producerClass.isInstance(myConfigurationProducer);
  }

  @Override
  public void onFirstRun(ConfigurationContext context, Runnable startRunnable) {
    myConfigurationProducer.onFirstRun(this, context, startRunnable);
  }

  @TestOnly
  public RunConfigurationProducer getConfigurationProducer() {
    return myConfigurationProducer;
  }
}
