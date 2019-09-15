// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote.target;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.remote.IR;
import com.intellij.execution.remote.RemoteTargetConfiguration;
import com.intellij.execution.remote.RemoteTargetConfigurationKt;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class IRExecutionTarget extends ExecutionTarget {
  @NotNull
  private final Project myProject;

  @NotNull
  private final RemoteTargetConfiguration myConfiguration;

  public IRExecutionTarget(@NotNull Project project, @NotNull RemoteTargetConfiguration configuration) {
    myProject = project;
    this.myConfiguration = configuration;
  }

  @NotNull
  public IR.RemoteRunner createRemoteRunner() {
    return myConfiguration.createRunner(myProject);
  }

  @NotNull
  @Override
  public String getId() {
    return myConfiguration.getTypeId() + "_" + myConfiguration.getDisplayName();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return myConfiguration.getDisplayName();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return RemoteTargetConfigurationKt.getTargetType(myConfiguration).getIcon();
  }

  @Override
  public boolean canRun(@NotNull RunConfiguration configuration) {
    return true;
  }
}