// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.NotNull;

public interface ConfigurationFactoryListener<T extends RunConfiguration> {
  default void onNewConfigurationCreated(@NotNull T configuration) {
  }

  default void onConfigurationCopied(@NotNull T configuration) {
  }
}
