// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link ConfigurationFactoryListener}
 */
@Deprecated
public abstract class ConfigurationFactoryEx<T extends RunConfiguration> extends ConfigurationFactory implements ConfigurationFactoryListener<T> {
  protected ConfigurationFactoryEx(@NotNull ConfigurationType type) {
    super(type);
  }
}
