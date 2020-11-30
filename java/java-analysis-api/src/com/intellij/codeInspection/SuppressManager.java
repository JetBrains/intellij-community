// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public abstract class SuppressManager implements BatchSuppressManager, InspectionSuppressor {

  public static SuppressManager getInstance() {
    return ApplicationManager.getApplication().getService(SuppressManager.class);
  }

  public static boolean isSuppressedInspectionName(PsiLiteralExpression expression) {
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class, true, PsiCodeBlock.class, PsiField.class, PsiCall.class);
    return annotation != null && SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(annotation.getQualifiedName());
  }

  @Override
  public SuppressQuickFix @NotNull [] createBatchSuppressActions(@NotNull HighlightDisplayKey key) {
    return BatchSuppressManager.SERVICE.getInstance().createBatchSuppressActions(key);
  }

  public abstract SuppressIntentionAction @NotNull [] createSuppressActions(@NotNull HighlightDisplayKey key);
}