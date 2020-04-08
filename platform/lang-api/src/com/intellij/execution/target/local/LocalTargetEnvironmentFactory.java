// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.local;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentFactory;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetPlatform;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalTargetEnvironmentFactory implements TargetEnvironmentFactory {
  @Nullable
  @Override
  public TargetEnvironmentConfiguration getTargetConfiguration() {
    return null;
  }

  @NotNull
  @Override
  public TargetPlatform getTargetPlatform() {
    return TargetPlatform.CURRENT;
  }

  @NotNull
  @Override
  public TargetEnvironmentRequest createRequest() {
    return new LocalTargetEnvironmentRequest();
  }

  @NotNull
  @Override
  public LocalTargetEnvironment prepareRemoteEnvironment(@NotNull TargetEnvironmentRequest request, @NotNull ProgressIndicator indicator) {
    return new LocalTargetEnvironment(request);
  }
}
