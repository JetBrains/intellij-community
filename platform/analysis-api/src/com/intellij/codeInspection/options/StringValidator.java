// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface that performs a custom validation of the string and optionally affects the UI to enter the string
 */
public interface StringValidator {
  /**
   * @return a globally unique identifier of validator kind
   */
  @NotNull String validatorId();

  /**
   * @param string string to check
   * @return an error message describing why the string is not valid; null if it's valid
   */
  @Nullable @NlsContexts.HintText String getErrorMessage(String string);
}
