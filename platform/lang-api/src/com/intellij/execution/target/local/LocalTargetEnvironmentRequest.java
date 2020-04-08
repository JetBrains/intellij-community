// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.local;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetPlatform;
import com.intellij.execution.target.value.TargetValue;
import org.jetbrains.annotations.NotNull;

public class LocalTargetEnvironmentRequest implements TargetEnvironmentRequest {
  @NotNull
  private GeneralCommandLine.ParentEnvironmentType myParentEnvironmentType = GeneralCommandLine.ParentEnvironmentType.CONSOLE;

  @NotNull
  @Override
  public TargetPlatform getTargetPlatform() {
    return TargetPlatform.CURRENT;
  }

  @NotNull
  @Override
  public TargetValue<String> createUpload(@NotNull String localPath) {
    return TargetValue.fixed(localPath);
  }

  @NotNull
  @Override
  public TargetValue<Integer> bindTargetPort(int targetPort) {
    return TargetValue.fixed(targetPort);
  }

  @NotNull
  GeneralCommandLine.ParentEnvironmentType getParentEnvironmentType() {
    return myParentEnvironmentType;
  }

  public void setParentEnvironmentType(@NotNull GeneralCommandLine.ParentEnvironmentType parentEnvironmentType) {
    myParentEnvironmentType = parentEnvironmentType;
  }
}
