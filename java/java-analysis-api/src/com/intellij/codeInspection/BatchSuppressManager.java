// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BatchSuppressManager {
  String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  static BatchSuppressManager getInstance() {
    return ApplicationManager.getApplication().getService(BatchSuppressManager.class);
  }

  SuppressQuickFix @NotNull [] createBatchSuppressActions(@NotNull HighlightDisplayKey key);

  boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId);

  @Nullable
  String getSuppressedInspectionIdsIn(@NotNull PsiElement element);

  @Nullable
  PsiElement getElementToolSuppressedIn(@NotNull PsiElement place, @NotNull String toolId);

  boolean canHave15Suppressions(@NotNull PsiElement file);

  boolean alreadyHas14Suppressions(@NotNull PsiDocCommentOwner commentOwner);
}
