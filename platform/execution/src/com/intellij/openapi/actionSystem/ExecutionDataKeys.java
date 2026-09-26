// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;

public final class ExecutionDataKeys {
  @SuppressWarnings({"deprecation", "unchecked"})
  public static final DataKey<ConsoleView> CONSOLE_VIEW = (DataKey<ConsoleView>)LangDataKeys.CONSOLE_VIEW;
  @SuppressWarnings({"deprecation", "unchecked"})
  public static final DataKey<ExecutionEnvironment> EXECUTION_ENVIRONMENT = (DataKey<ExecutionEnvironment>)LangDataKeys.EXECUTION_ENVIRONMENT;
  @SuppressWarnings({"deprecation", "unchecked"})
  public static final DataKey<RunContentDescriptor> RUN_CONTENT_DESCRIPTOR = (DataKey<RunContentDescriptor>)LangDataKeys.RUN_CONTENT_DESCRIPTOR;
  @SuppressWarnings({"deprecation", "unchecked"})
  public static final DataKey<RunProfile> RUN_PROFILE = (DataKey<RunProfile>)LangDataKeys.RUN_PROFILE;
}
