// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.CustomizableIntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IntentionWrapper implements LocalQuickFix, IntentionAction, ActionClassHolder, CustomizableIntentionActionDelegate {
  private final IntentionAction myAction;

  /**
   * @param action action to wrap
   */
  public IntentionWrapper(@NotNull IntentionAction action) {
    myAction = action;
  }

  @Override
  public @NotNull String getName() {
    return myAction.getText();
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

  public @NotNull IntentionAction getAction() {
    return myAction;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiFile file = element == null ? null : element.getContainingFile();
    if (file != null) {
      FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file.getVirtualFile());
      myAction.invoke(project, editor instanceof TextEditor ? ((TextEditor)editor).getEditor() : null, file);
    }
  }

  @Override
  public @NotNull Class<?> getActionClass() {
    return getAction().getClass();
  }

  @Override
  public @NotNull IntentionAction getDelegate() {
    return myAction;
  }

  @Contract("null, _ -> null")
  public static LocalQuickFix wrapToQuickFix(@Nullable IntentionAction action, @NotNull PsiFile file) {
    if (action == null) return null;
    if (action instanceof LocalQuickFix) return (LocalQuickFix)action;
    ModCommandAction modCommandAction = action.asModCommandAction();
    if (modCommandAction != null) {
      return LocalQuickFix.from(modCommandAction);
    }
    return new IntentionWrapper(action);
  }

  public static LocalQuickFix @NotNull [] wrapToQuickFixes(IntentionAction @NotNull [] actions, @NotNull PsiFile file) {
    if (actions.length == 0) return LocalQuickFix.EMPTY_ARRAY;
    LocalQuickFix[] fixes = new LocalQuickFix[actions.length];
    for (int i = 0; i < actions.length; i++) {
      fixes[i] = wrapToQuickFix(actions[i], file);
    }
    return fixes;
  }

  public static @NotNull List<@NotNull LocalQuickFix> wrapToQuickFixes(@NotNull List<? extends IntentionAction> actions, @NotNull PsiFile file) {
    if (actions.isEmpty()) return Collections.emptyList();
    List<LocalQuickFix> fixes = new ArrayList<>(actions.size());
    for (IntentionAction action : actions) {
      ContainerUtil.addIfNotNull(fixes, wrapToQuickFix(action, file));
    }
    return fixes;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile psiFile) {
    return myAction.generatePreview(project, editor, psiFile);
  }
}
