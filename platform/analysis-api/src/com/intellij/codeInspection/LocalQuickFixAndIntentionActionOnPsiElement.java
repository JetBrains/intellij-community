// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LocalQuickFixAndIntentionActionOnPsiElement extends LocalQuickFixOnPsiElement implements IntentionAction {
  protected LocalQuickFixAndIntentionActionOnPsiElement(@Nullable PsiElement element) {
    this(element, element);
  }

  protected LocalQuickFixAndIntentionActionOnPsiElement(@Nullable PsiElement startElement, @Nullable PsiElement endElement) {
    super(startElement, endElement);
  }

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (psiFile == null || myStartElement == null) return;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    if (startElement == null || endElement == null) return;
    invoke(project, psiFile, editor, startElement, endElement);
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (myStartElement == null) return false;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    return startElement != null &&
           endElement != null &&
           startElement.isValid() &&
           (endElement == startElement || endElement.isValid()) &&
           psiFile != null &&
           isAvailable(project, psiFile, editor, startElement, endElement);
  }

  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return isAvailable(project, psiFile, startElement, endElement);
  }

  /**
   * Performs the action.
   * @param editor the editor where the action is invoked or {@code null} if it's invoked from batch inspection results' tool window.
   */
  public abstract void invoke(@NotNull Project project,
                              @NotNull PsiFile psiFile,
                              @Nullable Editor editor,
                              @NotNull PsiElement startElement,
                              @NotNull PsiElement endElement);

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    invoke(project, psiFile, null, startElement, endElement);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  /**
   * This method exists to provide compatibility bridges. Use of it is discouraged. When possible, prefer
   * {@link ModCommandAction#asIntention()} or {@link LocalQuickFix#from(ModCommandAction)}
   * @param action action to delegate to
   * @param psiElement some context element. Mostly unused but should remain valid in order the action to be executed
   * @return a wrapper that extends {@link LocalQuickFixAndIntentionActionOnPsiElement} and delegates to the action
   */
  @ApiStatus.Internal
  public static LocalQuickFixAndIntentionActionOnPsiElement from(@NotNull ModCommandAction action, @NotNull PsiElement psiElement) {
    return ModCommandService.getInstance().wrapToLocalQuickFixAndIntentionActionOnPsiElement(action, psiElement);
  }
}
