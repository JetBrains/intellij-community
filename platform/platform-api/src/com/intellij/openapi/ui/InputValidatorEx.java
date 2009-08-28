package com.intellij.openapi.ui;

import org.jetbrains.annotations.Nullable;

/**
 * Allows to display error text in an input dialog for input strings that do not match
 * a certain condition.
 *
 * @author yole
 * @since 9.0
 */
public interface InputValidatorEx extends InputValidator {
  @Nullable
  String getErrorText(String inputString);
}
