// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.CustomizableIntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
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
   * @param file PsiFile to apply the action to (unused)
   * @deprecated use {@link IntentionWrapper#IntentionWrapper(IntentionAction)}
   */
  @Deprecated
  public IntentionWrapper(@NotNull IntentionAction action, @NotNull PsiFile file) {
    myAction = action;
  }

  /**
   * @param action action to wrap
   */
  public IntentionWrapper(@NotNull IntentionAction action) {
    myAction = action;
  }

  @NotNull
  @Override
  public String getName() {
    return myAction.getText();
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

  @NotNull
  public IntentionAction getAction() {
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

  @NotNull
  @Override
  public Class<?> getActionClass() {
    return getAction().getClass();
  }

  @NotNull
  @Override
  public IntentionAction getDelegate() {
    return myAction;
  }

  @Contract("null, _ -> null")
  public static LocalQuickFix wrapToQuickFix(@Nullable IntentionAction action, @NotNull PsiFile file) {
    if (action == null) return null;
    if (action instanceof LocalQuickFix) return (LocalQuickFix)action;
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

  @NotNull
  public static List<LocalQuickFix> wrapToQuickFixes(@NotNull List<? extends IntentionAction> actions, @NotNull PsiFile file) {
    if (actions.isEmpty()) return Collections.emptyList();
    List<LocalQuickFix> fixes = new ArrayList<>(actions.size());
    for (IntentionAction action : actions) {
      fixes.add(wrapToQuickFix(action, file));
    }
    return fixes;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    return myAction.generatePreview(project, editor, file);
  }
}
