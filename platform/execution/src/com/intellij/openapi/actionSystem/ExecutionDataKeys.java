// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.execution.runners.ExecutionEnvironment;

public final class ExecutionDataKeys {
  public static final DataKey<ExecutionEnvironment> EXECUTION_ENVIRONMENT = DataKey.create("executionEnvironment");
}
