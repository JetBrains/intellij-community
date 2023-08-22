// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalQuickFixOnPsiElementAsIntentionAdapter implements IntentionAction {
  private final @NotNull LocalQuickFixOnPsiElement myFix;

  public LocalQuickFixOnPsiElementAsIntentionAdapter(@NotNull LocalQuickFixOnPsiElement fix) {
    myFix = fix;
  }

  @Override
  public @NotNull String getText() {
    return myFix.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFix.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myFix.isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myFix.applyFix();
  }

  @Override
  public boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myFix.getElementToMakeWritable(currentFile);
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    LocalQuickFixOnPsiElement newFix = ObjectUtils.tryCast(myFix.getFileModifierForPreview(target), LocalQuickFixOnPsiElement.class);
    return newFix == null ? null : newFix == myFix ? this : new LocalQuickFixOnPsiElementAsIntentionAdapter(newFix);
  }
}

