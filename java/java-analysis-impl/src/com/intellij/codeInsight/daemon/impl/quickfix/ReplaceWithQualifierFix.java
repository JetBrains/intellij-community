// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithQualifierFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myRole;

  public ReplaceWithQualifierFix(@Nullable PsiMethodCallExpression call, @Nullable String role) {
    super(call);
    myRole = role;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiMethodCallExpression call = (PsiMethodCallExpression)startElement;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return;
    new CommentTracker().replace(call, qualifier);
  }

  @Override
  public @NotNull String getText() {
    return myRole == null ? QuickFixBundle.message("replace.with.qualifier.text") :
           QuickFixBundle.message("replace.with.qualifier.text.role", myRole);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.with.qualifier.text");
  }
}
