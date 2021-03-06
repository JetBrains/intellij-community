/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.options.ConfigurationException;
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
    catch (Throwable t) {
      return new ValidationInfo(t.getMessage(), component);
    }
  }
}