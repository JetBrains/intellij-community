// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ModCommand;
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
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (file == null || myStartElement == null) return;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    if (startElement == null || endElement == null) return;
    invoke(project, file, editor, startElement, endElement);
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myStartElement == null) return false;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    return startElement != null &&
           endElement != null &&
           startElement.isValid() &&
           (endElement == startElement || endElement.isValid()) &&
           file != null &&
           isAvailable(project, file, editor, startElement, endElement);
  }

  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return isAvailable(project, file, startElement, endElement);
  }

  /**
   * Performs the action.
   * @param editor the editor where the action is invoked or {@code null} if it's invoked from batch inspection results' tool window.
   */
  public abstract void invoke(@NotNull Project project,
                              @NotNull PsiFile file,
                              @Nullable Editor editor,
                              @NotNull PsiElement startElement,
                              @NotNull PsiElement endElement);

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    invoke(project, file, null, startElement, endElement);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  /**
   * This method exists to provide compatibility bridges. Use of it is discouraged. When possible, prefer
   * {@link ModCommandAction#asIntention()} or {@link ModCommandAction#asQuickFix()}
   * @param action action to delegate to
   * @param psiElement some context element. Mostly unused but should remain valid in order the action to be executed
   * @return a wrapper that extends {@link LocalQuickFixAndIntentionActionOnPsiElement} and delegates to the action
   */
  @ApiStatus.Internal
  public static LocalQuickFixAndIntentionActionOnPsiElement from(@NotNull ModCommandAction action, @NotNull PsiElement psiElement) {
    return new LocalQuickFixAndIntentionActionOnPsiElement(psiElement) {
      @Override
      public @NotNull String getFamilyName() {
        return action.getFamilyName();
      }

      @Override
      public @NotNull String getText() {
        PsiElement element = getStartElement();
        if (element == null) return getFamilyName();
        ModCommandAction.ActionContext context = ModCommandAction.ActionContext.from(null, element.getContainingFile())
          .withElement(element);
        ModCommandAction.Presentation presentation = action.getPresentation(context);
        if (presentation != null) {
          return presentation.name();
        }
        return getFamilyName();
      }

      @Override
      public boolean isAvailable(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @Nullable Editor editor,
                                 @NotNull PsiElement startElement,
                                 @NotNull PsiElement endElement) {
        ModCommandAction.ActionContext context = ModCommandAction.ActionContext.from(editor, file).withElement(startElement);
        return action.getPresentation(context) != null;
      }

      @Override
      public void invoke(@NotNull Project project,
                         @NotNull PsiFile file,
                         @Nullable Editor editor,
                         @NotNull PsiElement startElement,
                         @NotNull PsiElement endElement) {
        ModCommand command = action.perform(ModCommandAction.ActionContext.from(editor, file).withElement(startElement));
        ModCommandService.getInstance().executeInteractively(project, command);
      }

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        return action.generatePreview(ModCommandAction.ActionContext.from(previewDescriptor));
      }

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return action.generatePreview(ModCommandAction.ActionContext.from(editor, file));
      }
    };
  }
}
