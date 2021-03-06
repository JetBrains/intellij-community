// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class ValidationResult {
  public static final ValidationResult OK = new ValidationResult(null, null);

  private final @Nls String myErrorMessage;
  private final FacetConfigurationQuickFix myQuickFix;

  public ValidationResult(@Nls String errorMessage) {
    this(errorMessage, null);
  }

  public ValidationResult(@Nls String errorMessage, @Nullable FacetConfigurationQuickFix quickFix) {
    myErrorMessage = errorMessage;
    myQuickFix = quickFix;
  }

  public @Nls String getErrorMessage() {
    return myErrorMessage;
  }

  public FacetConfigurationQuickFix getQuickFix() {
    return myQuickFix;
  }

  public boolean isOk() {
    return myErrorMessage == null;
  }
}
