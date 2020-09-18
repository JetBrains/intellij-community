// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToRecord;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates the method or the field which will be weakened its visibility as the record introduces public accessors.
 */
class BrokenEncapsulationUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final String myErrMsg;

  BrokenEncapsulationUsageInfo(@NotNull PsiField psiField, @NotNull @Nls String errMsg) {
    super(psiField.getNameIdentifier());
    myErrMsg = errMsg;
  }

  BrokenEncapsulationUsageInfo(@NotNull PsiMethod psiMethod, @NotNull @Nls String errMsg) {
    super(psiMethod);
    myErrMsg = errMsg;
  }
}
