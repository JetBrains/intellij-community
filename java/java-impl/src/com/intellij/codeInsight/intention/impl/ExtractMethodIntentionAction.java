// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class ExtractMethodIntentionAction implements IntentionAction, Iconable {
  @NotNull
  @Override
  public String getText() {
    return JavaBundle.message("intention.extract.method.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file instanceof PsiCodeFragment || !file.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return false;
    }
    SelectionModel model = editor.getSelectionModel();
    if (!model.hasSelection()) return false;
    PsiElement[] elements = ExtractMethodHandler.getElements(project, editor, file);
    if (elements == null || elements.length == 0) return false;
    if (PsiTreeUtil.getParentOfType(elements[0], PsiClass.class) == null) return false;
    ExtractMethodProcessor processor = ExtractMethodHandler.getProcessor(project, elements, file, false);
    if (processor == null) return false;
    try {
      return processor.prepare(null);
    }
    catch (PrepareFailedException e) {
      return false;
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    new ExtractMethodHandler().invoke(project, editor, file, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.RefactoringBulb;
  }
}
