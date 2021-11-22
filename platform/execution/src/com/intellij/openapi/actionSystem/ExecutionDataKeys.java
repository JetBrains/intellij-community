// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.execution.runners.ExecutionEnvironment;

public class ExecutionDataKeys {
  public static final DataKey<ExecutionEnvironment> EXECUTION_ENVIRONMENT = DataKey.create("executionEnvironment");
}
