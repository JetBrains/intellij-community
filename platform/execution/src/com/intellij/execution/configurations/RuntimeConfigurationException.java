// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.util.ThrowableRunnable;

import javax.swing.*;

import static com.intellij.openapi.util.NlsContexts.DialogTitle;

public class RuntimeConfigurationException extends ConfigurationException {
  public RuntimeConfigurationException(@DialogMessage String message, @DialogTitle String title) {
    super(message, title);
  }

  public RuntimeConfigurationException(@DialogMessage String message) {
    super(message, ExecutionBundle.message("run.configuration.error.dialog.title"));
  }

  public RuntimeConfigurationException(@DialogMessage String message, Throwable cause) {
    super(message, cause, ExecutionBundle.message("run.configuration.error.dialog.title"));
  }

  public static <T extends Throwable> ValidationInfo validate(JComponent component, ThrowableRunnable<T> runnable) {
    try {
      runnable.run();
      return new ValidationInfo("", component);
    }
    catch (ProcessCanceledException e) {
      return new ValidationInfo("", component);
    }
    catch (Throwable t) {
      return new ValidationInfo(t.getMessage(), component);
    }
  }
}