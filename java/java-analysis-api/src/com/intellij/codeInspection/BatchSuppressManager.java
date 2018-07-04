// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BatchSuppressManager {
  String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  class SERVICE {
    public static BatchSuppressManager getInstance() {
      return ServiceManager.getService(BatchSuppressManager.class);
    }
  }
  @NotNull
  SuppressQuickFix[] createBatchSuppressActions(@NotNull HighlightDisplayKey key);

  boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId);

  @Nullable
  String getSuppressedInspectionIdsIn(@NotNull PsiElement element);

  @Nullable
  PsiElement getElementToolSuppressedIn(@NotNull PsiElement place, @NotNull String toolId);

  boolean canHave15Suppressions(@NotNull PsiElement file);

  boolean alreadyHas14Suppressions(@NotNull PsiDocCommentOwner commentOwner);
}
