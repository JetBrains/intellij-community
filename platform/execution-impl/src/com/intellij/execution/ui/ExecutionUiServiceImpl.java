// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExecutionUiServiceImpl extends ExecutionUiService {
  @Override
  @Nullable
  public RunContentDescriptor showRunContent(@NotNull ExecutionResult executionResult,
                                             @NotNull ExecutionEnvironment environment) {
    return new RunContentBuilder(executionResult, environment).showRunContent(environment.getContentToReuse());
  }

  @Override
  public void assertTimeConsuming() {
    ApplicationManagerEx.getApplicationEx().assertTimeConsuming();
  }
}
