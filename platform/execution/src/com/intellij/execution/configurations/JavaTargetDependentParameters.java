// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@ApiStatus.Experimental
public final class JavaTargetDependentParameters {
  private final List<Function<TargetEnvironmentRequest, JavaTargetParameter>> parameters = new ArrayList<>();
  private TargetEnvironment myEnvironment;

  public void addParameter(@NotNull Function<TargetEnvironmentRequest, JavaTargetParameter> parameter) {
    parameters.add(parameter);
  }

  @NotNull
  public List<String> toLocalParameters() {
    return ContainerUtil.map(parameters, (f) -> f.apply(new LocalTargetEnvironmentRequest()).toLocalParameter());
  }

  @NotNull
  public List<Function<TargetEnvironmentRequest, JavaTargetParameter>> asTargetParameters() {
    return parameters;
  }

  public void setTargetEnvironment(@NotNull TargetEnvironment environment) {
    myEnvironment = environment;
  }

  @Nullable
  public TargetEnvironment getTargetEnvironment() {
    return myEnvironment;
  }
}