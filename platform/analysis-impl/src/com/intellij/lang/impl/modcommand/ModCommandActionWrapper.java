// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

/**
 * A bridge from {@link ModCommandAction} to {@link IntentionAction} interface.
 */
@ApiStatus.Internal
public final class ModCommandActionWrapper implements IntentionAction, PriorityAction, Iconable, IntentionActionWithFixAllOption,
                                                                   CustomizableIntentionAction, ReportingClassSubstitutor, PossiblyDumbAware {
  private final @NotNull ModCommandAction myModAction;
  private @Nullable Presentation myPresentation;

  public ModCommandActionWrapper(@NotNull ModCommandAction modAction, @Nullable Presentation presentation) {
    this.myModAction = modAction;
    this.myPresentation = presentation;
  }

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
      return "(not initialized) " + myModAction.getClass(); //NON-NLS
    }
    return myPresentation.name();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myModAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!DumbService.getInstance(project).isUsableInCurrentContext(myModAction)) return false;
    Presentation presentation = myModAction.getPresentation(ActionContext.from(editor, psiFile));
    if (presentation == null) return false;
    myPresentation = presentation;
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    ActionContext context = ActionContext.from(editor, psiFile);
    ModCommand command = myModAction.perform(context);
    ModCommandExecutor instance = ModCommandExecutor.getInstance();
    if (psiFile.isPhysical()) {
      instance.executeInteractively(context, command, editor);
    } else {
      instance.executeForFileCopy(command, psiFile);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return myModAction.generatePreview(ActionContext.from(editor, psiFile));
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPresentation == null ? Priority.NORMAL : myPresentation.priority();
  }

  @Override
  public Icon getIcon(int flags) {
    return myPresentation == null ? null : myPresentation.icon();
  }

  @Override
  public @NotNull List<IntentionAction> getOptions() {
    return myPresentation != null && myPresentation.fixAllOption() != null ?
           IntentionActionWithFixAllOption.super.getOptions() : List.of();
  }

  @Override
  public @Unmodifiable @NotNull List<RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    if (myPresentation == null) return List.of();
    return ContainerUtil.map(myPresentation.rangesToHighlight(), range -> new RangeToHighlight(file, range.range(), range.highlightKey()));
  }
  
  @Override
  public boolean belongsToMyFamily(@NotNull IntentionActionWithFixAllOption action) {
    ModCommandAction unwrapped = action.asModCommandAction();
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
    return Objects.equals(this.myModAction, that.myModAction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModAction);
  }

  @Override
  public String toString() {
    return "ModCommandActionWrapper[" +
           "action=" + myModAction + ']';
  }

  @Override
  public @NotNull ModCommandAction asModCommandAction() {
    return myModAction;
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(myModAction);
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(myModAction);
  }
}
