// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ReplaceConstructorWithBuilderAction extends PsiElementBaseIntentionAction {

  @NotNull
  @Override
  public String getText() {
    return JavaRefactoringBundle.message("replace.constructor.with.builder.text");
  }

  @NotNull
  @Override
  public final String getFamilyName() {
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

  @Nullable
  private static PsiMethod getConstructor(@Nullable PsiElement element) {
    PsiMethod method = RefactoringActionContextUtil.getJavaMethodHeader(element);
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