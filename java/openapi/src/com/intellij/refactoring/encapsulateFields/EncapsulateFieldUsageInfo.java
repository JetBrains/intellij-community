// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
* @author Max Medvedev
*/
public class EncapsulateFieldUsageInfo extends UsageInfo {
  private final FieldDescriptor myFieldDescriptor;

  public EncapsulateFieldUsageInfo(PsiReference ref, @NotNull FieldDescriptor descriptor) {
    super(ref);
    myFieldDescriptor = descriptor;
  }

  public @NotNull FieldDescriptor getFieldDescriptor() {
    return myFieldDescriptor;
  }
}
