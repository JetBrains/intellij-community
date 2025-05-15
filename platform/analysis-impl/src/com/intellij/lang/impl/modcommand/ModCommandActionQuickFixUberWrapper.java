// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.NewUiValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

@ApiStatus.Internal
public final class ModCommandActionQuickFixUberWrapper extends LocalQuickFixAndIntentionActionOnPsiElement
  implements Iconable, PriorityAction, IntentionActionWithFixAllOption, ReportingClassSubstitutor {
  private final @NotNull ModCommandAction myAction;
  private @Nullable Presentation myPresentation;

  public ModCommandActionQuickFixUberWrapper(@NotNull ModCommandAction action, @NotNull PsiElement element) {
    super(element);
    myAction = action;
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public @NotNull String getText() {
    Presentation presentation = getPresentation();
    if (presentation != null) {
      return presentation.name();
    }
    return getFamilyName();
  }

  private @Nullable Presentation getPresentation() {
    if (myPresentation != null) return myPresentation;
    PsiElement element = getStartElement();
    if (element == null) return null;
    ActionContext context = ActionContext.from(null, element.getContainingFile())
      .withElement(element);
    return myAction.getPresentation(context);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    ActionContext context = ActionContext.from(editor, psiFile).withElement(startElement);
    myPresentation = myAction.getPresentation(context);
    return myPresentation != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    ActionContext context = ActionContext.from(editor, psiFile).withElement(startElement);
    Presentation presentation = myPresentation;
    String name = presentation == null ? getFamilyName() : presentation.name();
    ModCommandExecutor.executeInteractively(context, name, editor, () -> myAction.perform(context));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return myAction.generatePreview(ActionContext.from(previewDescriptor));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return myAction.generatePreview(ActionContext.from(editor, psiFile));
  }

  @Override
  public @NotNull PriorityAction.Priority getPriority() {
    Presentation presentation = getPresentation();
    return presentation == null ? Priority.NORMAL : presentation.priority();
  }

  @Override
  public Icon getIcon(int flags) {
    if (NewUiValue.isEnabled()) return null;
    Presentation presentation = getPresentation();
    return presentation == null ? null : presentation.icon();
  }

  @Override
  public @NotNull List<IntentionAction> getOptions() {
    Presentation presentation = getPresentation();
    return presentation != null && presentation.fixAllOption() != null ?
           IntentionActionWithFixAllOption.super.getOptions() : List.of();
  }

  @Override
  public boolean belongsToMyFamily(@NotNull IntentionActionWithFixAllOption action) {
    Presentation presentation = getPresentation();
    ModCommandAction unwrapped = action.asModCommandAction();
    if (unwrapped == null || presentation == null || presentation.fixAllOption() == null) return false;
    return presentation.fixAllOption().belongsToMyFamily().test(unwrapped);
  }

  @Override
  public @NotNull String getFixAllText() {
    Presentation presentation = getPresentation();
    return presentation != null && presentation.fixAllOption() != null ?
           presentation.fixAllOption().name() : "";
  }

  @Override
  public String toString() {
    return "[LocalQuickFixAndIntentionActionOnPsiElement] " + myAction;
  }

  @Override
  public @Nullable ModCommandAction asModCommandAction() {
    return myAction;
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(myAction);
  }
}
