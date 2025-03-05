// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@ApiStatus.Experimental
public final class JavaTargetDependentParameters {
  private final List<Function<? super TargetEnvironmentRequest, ? extends JavaTargetParameter>> parameters = new ArrayList<>();
  private TargetEnvironment myEnvironment;

  public void addParameter(@NotNull Function<? super TargetEnvironmentRequest, ? extends JavaTargetParameter> parameter) {
    parameters.add(parameter);
  }

  public @NotNull @Unmodifiable List<String> toLocalParameters() {
    return ContainerUtil.map(parameters, f -> f.apply(new LocalTargetEnvironmentRequest()).toLocalParameter());
  }

  public @NotNull List<Function<? super TargetEnvironmentRequest, ? extends JavaTargetParameter>> asTargetParameters() {
    return parameters;
  }

  public void setTargetEnvironment(@NotNull TargetEnvironment environment) {
    myEnvironment = environment;
  }

  public @Nullable TargetEnvironment getTargetEnvironment() {
    return myEnvironment;
  }
}