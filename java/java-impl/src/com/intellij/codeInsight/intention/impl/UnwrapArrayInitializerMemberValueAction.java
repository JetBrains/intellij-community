// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnwrapArrayInitializerMemberValueAction extends LocalQuickFixAndIntentionActionOnPsiElement {
  public UnwrapArrayInitializerMemberValueAction(@NotNull PsiArrayInitializerMemberValue arrayValue) {
    super(arrayValue);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiArrayInitializerMemberValue arrayValue = ObjectUtils.tryCast(startElement, PsiArrayInitializerMemberValue.class);
    if (arrayValue == null) return false;
    return arrayValue.getInitializers().length == 1;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiArrayInitializerMemberValue arrayValue = ObjectUtils.tryCast(startElement, PsiArrayInitializerMemberValue.class);
    if (arrayValue == null) return;
    final CommentTracker ct = new CommentTracker();
    ct.replaceAndRestoreComments(arrayValue, arrayValue.getInitializers()[0]);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return QuickFixBundle.message("unwrap.array.initializer.member.value.fix");
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return getText();
  }
}
