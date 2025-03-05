// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule, Element> {
  private final Map<String, String> myEnvs = new LinkedHashMap<>();
  private boolean myPassParentEnvs = true;

  public AbstractRunConfiguration(String name, @NotNull RunConfigurationModule configurationModule, @NotNull ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public AbstractRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(new RunConfigurationModule(project), factory);
  }

  public @NotNull Map<String, String> getEnvs() {
    return myEnvs;
  }

  public void setEnvs(final @NotNull Map<String, String> envs) {
    myEnvs.clear();
    myEnvs.putAll(envs);
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(final boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
  }
}
