// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PriorityIntentionActionWrapper implements IntentionAction, PriorityAction, IntentionActionDelegate {
  private final IntentionAction myAction;
  private final Priority myPriority;

  private PriorityIntentionActionWrapper(@NotNull IntentionAction action, @NotNull Priority priority) {
    myAction = action;
    myPriority = priority;
  }

  @Override
  public @NotNull String getText() {
    return myAction.getText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myAction.isAvailable(project, editor, psiFile);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    myAction.invoke(project, editor, psiFile);
  }
  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myAction.getElementToMakeWritable(file);
  }

  @Override
  public boolean startInWriteAction() {
    return myAction.startInWriteAction();
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPriority;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return myAction.generatePreview(project, editor, psiFile);
  }

  @Override
  public @NotNull IntentionAction getDelegate() {
    return myAction;
  }

  public static @NotNull IntentionAction highPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.HIGH);
  }

  public static @NotNull IntentionAction normalPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.NORMAL);
  }

  public static @NotNull IntentionAction lowPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.LOW);
  }
}
