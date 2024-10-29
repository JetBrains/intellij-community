// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

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

  FieldUsageInfo(@NotNull PsiField psiField, @NotNull PsiReference ref) {
    super(ref);
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
  final @DialogMessage String myErrMsg;

  BrokenEncapsulationUsageInfo(@NotNull PsiField psiField, @NotNull @DialogMessage String errMsg) {
    super(psiField.getNameIdentifier());
    myErrMsg = errMsg;
  }

  BrokenEncapsulationUsageInfo(@NotNull PsiMethod psiMethod, @NotNull @DialogMessage String errMsg) {
    super(psiMethod);
    myErrMsg = errMsg;
  }
}