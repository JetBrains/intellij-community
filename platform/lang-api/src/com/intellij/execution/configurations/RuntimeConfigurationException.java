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
import org.jetbrains.annotations.Nullable;

public class RuntimeConfigurationException extends ConfigurationException {
  // Editor name in run configuration settings which contains error
  // and should be focused.
  @Nullable private final String myFocusedEditorName;

  public RuntimeConfigurationException(@Nullable final String message,
                                       @Nullable final String title,
                                       @Nullable final String focusedEditorName) {
    super(message, title);
    myFocusedEditorName = focusedEditorName;
  }

  public RuntimeConfigurationException(@Nullable final String message, @Nullable final String title) {
    this(message, title, null);
  }

  public RuntimeConfigurationException(@Nullable final String message) {
    this(message, ExecutionBundle.message("run.configuration.error.dialog.title"), null);
  }

  @Nullable
  public String getFocusedEditorName() {
    return myFocusedEditorName;
  }
}