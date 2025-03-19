// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to display error text in an input dialog for input strings that do not match
 * a certain condition.
 */
public interface InputValidatorEx extends InputValidator {
  @NlsContexts.DetailedDescription
  @Nullable
  String getErrorText(@NonNls String inputString);

  default @NlsContexts.DetailedDescription @Nullable String getWarningText(@NonNls String inputString) {
    return null;
  }

  /**
   * @return {@code true} iff there are no errors
   */
  @Override
  default boolean checkInput(String inputString) {
    return getErrorText(inputString) == null;
  }

  @Override
  default boolean canClose(String inputString) {
    return true;
  }
}
