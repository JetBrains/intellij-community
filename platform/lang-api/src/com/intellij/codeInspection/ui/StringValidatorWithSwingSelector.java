// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.options.StringValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A kind of validator that can display a custom Swing UI (dialog box) to conveniently 
 * enter a valid string.
 */
public interface StringValidatorWithSwingSelector extends StringValidator {
  /**
   * Displays Swing UI (dialog) that prompts for a new item. 
   * Could be run in DumbMode or with a default project
   * @param project project whose context the dialog is displayed in
   * @return a new item selected by user; null if user dismisses the prompt
   */
  @Nullable String select(@NotNull Project project);
}
