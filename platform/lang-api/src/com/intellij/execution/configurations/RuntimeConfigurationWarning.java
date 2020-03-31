// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import org.jetbrains.annotations.Nls;

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class RuntimeConfigurationWarning extends RuntimeConfigurationException {
  public RuntimeConfigurationWarning(@Nls(capitalization = Nls.Capitalization.Sentence) final String message) {
    this(message, null);
  }

  public RuntimeConfigurationWarning(@Nls(capitalization = Nls.Capitalization.Sentence) final String message, final Runnable quickFix) {
    super(message, ExecutionBundle.message("warning.common.title"));
    setQuickFix(quickFix);
  }
}