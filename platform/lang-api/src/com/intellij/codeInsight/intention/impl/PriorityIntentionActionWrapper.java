// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public final class PriorityIntentionActionWrapper implements IntentionAction, PriorityAction {
  private final IntentionAction myAction;
  private final Priority myPriority;

  private PriorityIntentionActionWrapper(@NotNull IntentionAction action, @NotNull Priority priority) {
    myAction = action;
    myPriority = priority;
  }

  @NotNull
  @Override
  public String getText() {
    return myAction.getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myAction.isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myAction.invoke(project, editor, file);
  }
  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
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
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    IntentionAction delegate = ObjectUtils.tryCast(myAction.getFileModifierForPreview(target), IntentionAction.class);
    return delegate == null ? null :
           delegate == myAction ? this :
           new PriorityIntentionActionWrapper(delegate, myPriority);
  }

  @NotNull
  public static IntentionAction highPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.HIGH);
  }

  @NotNull
  public static IntentionAction normalPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.NORMAL);
  }

  @NotNull
  public static IntentionAction lowPriority(@NotNull IntentionAction action) {
    return new PriorityIntentionActionWrapper(action, Priority.LOW);
  }
}
