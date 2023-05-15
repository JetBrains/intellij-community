// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModStatus;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

/**
 * A bridge from {@link ModCommandAction} to {@link IntentionAction} interface.
 */
/*package*/ final class ModCommandActionWrapper implements IntentionAction, PriorityAction, Iconable, IntentionActionWithFixAllOption {
  private final @NotNull ModCommandAction myAction;
  private @Nullable ModCommandAction.Presentation myPresentation;

  ModCommandActionWrapper(@NotNull ModCommandAction action) { this.myAction = action; }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return null;
  }

  @Override
  public @NotNull String getText() {
    if (myPresentation == null) {
      // Should not arrive here; for debug purposes only
      return "(not initialized) " + myAction.getClass(); //NON-NLS
    }
    return myPresentation.name();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    ModCommandAction.Presentation presentation = myAction.getPresentation(ModCommandAction.ActionContext.from(editor, file));
    if (presentation == null) return false;
    myPresentation = presentation;
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ModCommand command = myAction.perform(ModCommandAction.ActionContext.from(editor, file));
    command.execute(project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    throw new UnsupportedOperationException("Should not be called directly");
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPresentation == null ? Priority.NORMAL : myPresentation.priority();
  }

  @Override
  public Icon getIcon(int flags) {
    return myPresentation == null ? null : myPresentation.icon();
  }

  public @NotNull ModCommandAction action() { return myAction; }

  @Override
  public @NotNull List<IntentionAction> getOptions() {
    return myPresentation != null && myPresentation.fixAllOption() != null ?
           IntentionActionWithFixAllOption.super.getOptions() : List.of();
  }

  @Override
  public boolean belongsToMyFamily(@NotNull IntentionActionWithFixAllOption action) {
    ModCommandAction unwrapped = ModCommandAction.unwrap(action);
    if (unwrapped == null || myPresentation == null || myPresentation.fixAllOption() == null) return false;
    return myPresentation.fixAllOption().belongsToMyFamily().test(unwrapped);
  }

  @Override
  public @NotNull String getFixAllText() {
    return myPresentation != null && myPresentation.fixAllOption() != null ?
           myPresentation.fixAllOption().name() : "";
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ModCommandActionWrapper)obj;
    return Objects.equals(this.myAction, that.myAction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myAction);
  }

  @Override
  public String toString() {
    return "ModCommandActionWrapper[" +
           "action=" + myAction + ']';
  }
}
