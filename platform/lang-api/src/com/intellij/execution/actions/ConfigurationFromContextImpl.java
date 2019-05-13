/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
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
}
