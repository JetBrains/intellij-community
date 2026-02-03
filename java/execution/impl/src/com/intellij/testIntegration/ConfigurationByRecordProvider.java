// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ConfigurationByRecordProvider {
  RunnerAndConfigurationSettings getConfiguration(TestStateStorage.Record record);
}
