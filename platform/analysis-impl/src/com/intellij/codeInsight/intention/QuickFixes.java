// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class QuickFixes {
  public static final LocalQuickFixAndIntentionActionOnPsiElement EMPTY_FIX = new LocalQuickFixAndIntentionActionOnPsiElement(null) {
    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @Nullable Editor editor, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull String getText() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull String getFamilyName() {
      throw new UnsupportedOperationException();
    }
  };
}