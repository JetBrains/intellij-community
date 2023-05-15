// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * An interface that performs a custom validation of the string and optionally affects the UI to enter the string
 */
public interface StringValidator {
  /**
   * @return a globally unique identifier of validator kind
   */
  @NotNull String validatorId();

  /**
   * Could be run in DumbMode, could be run in EDT or with a default project, should be thread-safe
   * @param project project in which context the string must be checked; null if unknown/non-applicable
   * @param string string to check
   * @return an error message describing why the string is not valid; null if it's valid
   */
  @Nullable @NlsContexts.HintText String getErrorMessage(@Nullable Project project, @NotNull String string);

  /**
   * Creates a validator from lambda function 
   * @param validatorId a globally unique identifier of validator kind
   * @param fn function to apply to the string value that returns the error message 
   *           describing why the string is not valid; or returns null if it's valid
   * @return the validator that applies the supplied function.
   */
  static @NotNull StringValidator of(@NotNull String validatorId,
                                     @NotNull Function<@NotNull String, @NlsContexts.HintText @Nullable String> fn) {
    return new StringValidator() {
      @Override
      public @NotNull String validatorId() {
        return validatorId;
      }

      @Override
      public @Nullable String getErrorMessage(@Nullable Project project, @NotNull String string) {
        return fn.apply(string);
      }
    };
  }
}
