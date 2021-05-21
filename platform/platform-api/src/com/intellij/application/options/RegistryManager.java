// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;

public interface RegistryManager {
  static @NotNull RegistryManager getInstance() {
    return ApplicationManager.getApplication().getService(RegistryManager.class);
  }

  boolean is(@NotNull String key);

  int intValue(@NotNull String key);

  int intValue(@NotNull String key, int defaultValue);

  @NotNull
  RegistryValue get(@NotNull String key);
}