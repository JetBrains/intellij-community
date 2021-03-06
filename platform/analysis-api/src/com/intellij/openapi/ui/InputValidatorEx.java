// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to display error text in an input dialog for input strings that do not match
 * a certain condition.
 *
 * @author yole
 */
public interface InputValidatorEx extends InputValidator {
  @NlsContexts.DetailedDescription
  @Nullable
  String getErrorText(@NonNls String inputString);
}
