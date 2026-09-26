// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNullByDefault;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Marker interface to distinguish custom usage info from possible existing platform ones.
 */
@NotNullByDefault
sealed interface ConvertToRecordUsageInfo permits FieldUsageInfo, RenameMethodUsageInfo, BrokenEncapsulationUsageInfo {
}

/**
 * Encapsulates the field which will become less accessible the record introduces a private final field.
 */
@NotNullByDefault
final class FieldUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final PsiField field;

  FieldUsageInfo(PsiField field, PsiReference reference) {
    super(reference);
    this.field = field;
  }

  @Override
  public String toString() {
    return "FieldUsageInfo(" + field + ")";
  }
}

/**
 * Encapsulates the method which will be renamed by its new name as the record specifies accessors naming.
 */
@NotNullByDefault
final class RenameMethodUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final PsiMethod method;
  final String newName;

  RenameMethodUsageInfo(PsiMethod method, String newName) {
    super(method);
    this.method = method;
    this.newName = newName;
  }

  @Override
  public String toString() {
    return "RenameMethodUsageInfo(" + method + ", newName: " + newName + ")";
  }
}

/**
 * Encapsulates the method or the field which becomes more accessible.
 */
@NotNullByDefault
final class BrokenEncapsulationUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final @DialogMessage String errorMessage;

  BrokenEncapsulationUsageInfo(PsiField psiField, @DialogMessage String errorMessage) {
    super(psiField.getNameIdentifier());
    this.errorMessage = errorMessage;
  }

  BrokenEncapsulationUsageInfo(PsiMethod psiMethod, @DialogMessage String errorMessage) {
    super(psiMethod);
    this.errorMessage = errorMessage;
  }

  @Override
  public String toString() {
    return "BrokenEncapsulationUsageInfo(" + errorMessage + ")";
  }
}
