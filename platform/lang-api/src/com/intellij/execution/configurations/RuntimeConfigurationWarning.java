// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.util.NlsContexts.DialogMessage;

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class RuntimeConfigurationWarning extends RuntimeConfigurationException {
  public RuntimeConfigurationWarning(@DialogMessage String message) {
    this(message, null);
  }

  public RuntimeConfigurationWarning(@DialogMessage String message, final Runnable quickFix) {
    super(message, ExecutionBundle.message("warning.common.title"));
    setQuickFix(quickFix);
  }
}