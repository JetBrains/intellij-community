// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.JavaRefactoringService;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.codeFloatingToolbar.CodeFloatingToolbar;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;


public final class ExtractMethodIntentionAction implements IntentionAction, Iconable {
  @Override
  public @NotNull String getText() {
    return JavaBundle.message("intention.extract.method.text");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    CodeFloatingToolbar floatingToolbar = CodeFloatingToolbar.getToolbar(editor);
    if (floatingToolbar != null && (floatingToolbar.isShown() || floatingToolbar.canBeShownAtCurrentSelection())) return false;
    if (psiFile instanceof PsiCodeFragment || !psiFile.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return false;
    }
    SelectionModel model = editor.getSelectionModel();
    if (!model.hasSelection()) return false;
    JavaRefactoringService refactoringService = JavaRefactoringService.getInstance();
    return refactoringService != null && refactoringService.canExtractMethod(project, editor, psiFile);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    JavaRefactoringService refactoringService = JavaRefactoringService.getInstance();
    if (refactoringService == null) return;
    refactoringService.extractMethod(project, editor, psiFile);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public Icon getIcon(int flags) {
    return ExperimentalUI.isNewUI() ? null : AllIcons.Actions.RefactoringBulb;
  }
}
