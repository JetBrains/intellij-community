// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressLocalWithCommentFix extends SuppressByJavaCommentFix {
  public SuppressLocalWithCommentFix(@NotNull HighlightDisplayKey key) {
    super(key);
  }

  @Override
  public @Nullable PsiElement getContainer(PsiElement context) {
    final PsiElement container = super.getContainer(context);
    if (container != null) {
      final PsiElement elementToAnnotate = JavaSuppressionUtil.getElementToAnnotate(context, container);
      if (elementToAnnotate == null) return null;
    }
    return container;
  }

  @Override
  protected PsiElement getElementToAnnotate(@NotNull PsiElement element, @NotNull PsiElement container) {
    return null;
  }

  @Override
  public @NotNull String getText() {
    return JavaAnalysisBundle.message("suppress.for.statement.with.comment");
  }

  @Override
  public int getPriority() {
    return 20;
  }
}
