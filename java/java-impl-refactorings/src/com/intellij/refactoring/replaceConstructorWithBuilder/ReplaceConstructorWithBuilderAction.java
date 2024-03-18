// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public final class ReplaceConstructorWithBuilderAction extends PsiElementBaseIntentionAction implements Iconable {

  @NotNull
  @Override
  public String getText() {
    return JavaRefactoringBundle.message("replace.constructor.with.builder.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return getConstructor(element) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final @Nullable PsiMethod constructor = getConstructor(element);
    if (constructor == null) {
      return;
    }

    PsiClass aClass = constructor.getContainingClass();
    new ReplaceConstructorWithBuilderDialog(project, Objects.requireNonNull(aClass).getConstructors()).show();
  }

  @Override
  public Icon getIcon(int flags) {
    return ExperimentalUI.isNewUI() ? null : AllIcons.Actions.RefactoringBulb;
  }

  @Nullable
  private static PsiMethod getConstructor(@Nullable PsiElement element) {
    PsiMethod method = MethodUtils.getJavaMethodFromHeader(element);
    if (method != null && method.isConstructor()) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && 
          !aClass.isEnum() && 
          method.getName().equals(aClass.getName())) {
        return method;
      }
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}