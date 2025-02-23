// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.WorkingDirectoryProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

@ApiStatus.Internal
public class ExternalSystemWorkingDirectoryProvider implements WorkingDirectoryProvider {
  @Override
  public @Nullable @SystemIndependent String getWorkingDirectoryPath(@NotNull Module module) {
    return ExternalSystemApiUtil.getExternalProjectPath(module);
  }
}
