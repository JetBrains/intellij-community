// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class DefaultExecutionTargetProvider extends ExecutionTargetProvider {
  @Override
  public @NotNull List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunConfiguration configuration) {
    return Collections.singletonList(DefaultExecutionTarget.INSTANCE);
  }
}
