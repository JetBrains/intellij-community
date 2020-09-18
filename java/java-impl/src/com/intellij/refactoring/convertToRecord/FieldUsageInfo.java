// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToRecord;

import com.intellij.psi.PsiField;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates the field which will be narrowed its visibility as the record introduces a private final field.
 */
class FieldUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final PsiField myField;

  FieldUsageInfo(@NotNull PsiField psiField) {
    super(psiField);
    myField = psiField;
  }
}
