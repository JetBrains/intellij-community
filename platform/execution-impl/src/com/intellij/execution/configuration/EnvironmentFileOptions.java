// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/// Use [com.intellij.execution.EnvFilesOptions] instead
@ApiStatus.Internal
@ApiStatus.Obsolete
public interface EnvironmentFileOptions {
  void setEnvironmentFile(@Nullable String file);

  @Nullable
  String getEnvironmentFile();
}
