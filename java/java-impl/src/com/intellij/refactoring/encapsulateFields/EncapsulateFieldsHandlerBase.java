// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

public interface EncapsulateFieldsHandlerBase extends RefactoringActionHandler {
  void invokeForPreview(@NotNull PsiField field);
}
