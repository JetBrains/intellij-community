// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import org.jetbrains.annotations.NotNull;

public abstract class JavaTestConfigurationWithDiscoverySupport extends JavaTestConfigurationBase {
  public JavaTestConfigurationWithDiscoverySupport(String name,
                                                   @NotNull JavaRunConfigurationModule configurationModule,
                                                   @NotNull ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public abstract byte getTestFrameworkId();
}
