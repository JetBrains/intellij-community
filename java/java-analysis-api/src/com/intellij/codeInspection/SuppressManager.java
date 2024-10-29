// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.codeInsight.DumbAwareAnnotationUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public abstract class SuppressManager implements BatchSuppressManager, InspectionSuppressor {

  public static SuppressManager getInstance() {
    return ApplicationManager.getApplication().getService(SuppressManager.class);
  }

  public static boolean isSuppressedInspectionName(PsiLiteralExpression expression) {
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class, true, PsiCodeBlock.class, PsiField.class, PsiCall.class);
    return annotation != null &&
           (!DumbService.isDumb(expression.getProject()) && SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(annotation.getQualifiedName()) ||
           DumbAwareAnnotationUtil.isAnnotationMatchesFqn(annotation, SUPPRESS_INSPECTIONS_ANNOTATION_NAME));
  }

  @Override
  public SuppressQuickFix @NotNull [] createBatchSuppressActions(@NotNull HighlightDisplayKey key) {
    return BatchSuppressManager.getInstance().createBatchSuppressActions(key);
  }

  public abstract SuppressIntentionAction @NotNull [] createSuppressActions(@NotNull HighlightDisplayKey key);
}