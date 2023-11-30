// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;

final class DefaultChangeLocalityDetector implements ChangeLocalityDetector {
  @Override
  public PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement) {
    if (changedElement instanceof PsiWhiteSpace ||
        changedElement instanceof PsiComment
        && !changedElement.getText().contains(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) {
      return changedElement;
    }
    return null;
  }
}
