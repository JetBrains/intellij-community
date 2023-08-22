// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.options.ConfigurationQuickFix;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class RuntimeConfigurationWarning extends RuntimeConfigurationException {
  public RuntimeConfigurationWarning(@DialogMessage String message) {
    super(message, ExecutionBundle.message("warning.common.title"));
  }

  public RuntimeConfigurationWarning(@DialogMessage String message, @Nullable ConfigurationQuickFix quickFix) {
    super(message, ExecutionBundle.message("warning.common.title"));
    setQuickFix(quickFix);
  }

  public RuntimeConfigurationWarning(@DialogMessage String message, @Nullable Runnable quickFix) {
    super(message, ExecutionBundle.message("warning.common.title"));
    setQuickFix(quickFix);
  }
}