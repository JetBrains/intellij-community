// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface to distinguish custom usage info from possible existing platform ones.
 */
interface ConvertToRecordUsageInfo {
}

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

/**
 * Encapsulates the method which will be renamed by its new name as the record specifies accessors naming.
 */
class RenameMethodUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final PsiMethod myMethod;
  final String myNewName;

  RenameMethodUsageInfo(@NotNull PsiMethod method, @NotNull String name) {
    super(method);
    myMethod = method;
    myNewName = name;
  }
}

/**
 * Encapsulates the method or the field which becomes more accessible.
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