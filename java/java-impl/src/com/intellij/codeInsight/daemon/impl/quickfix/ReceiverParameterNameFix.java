// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReceiverParameter;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReceiverParameterNameFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myNewName;

  public ReceiverParameterNameFix(@NotNull PsiReceiverParameter parameter, @NotNull String newName) {
    super(parameter);
    myNewName = newName;
  }

  @Override
  @NotNull
  public String getText() {
    return CommonQuickFixBundle.message("fix.replace.with.x", myNewName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("fix.receiver.parameter.name.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    CommentTracker ct = new CommentTracker();
    ct.replaceExpressionAndRestoreComments(((PsiReceiverParameter)startElement).getIdentifier(), myNewName);
  }
}