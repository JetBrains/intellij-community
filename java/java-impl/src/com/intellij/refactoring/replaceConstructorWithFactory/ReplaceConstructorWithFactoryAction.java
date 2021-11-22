// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReplaceConstructorWithFactoryAction extends PsiElementBaseIntentionAction implements Iconable {

  @NotNull
  @Override
  public String getText() {
    return JavaRefactoringBundle.message("replace.constructor.with.factory.method");
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

    final ReplaceConstructorWithFactoryHandler handler = new ReplaceConstructorWithFactoryHandler();
    handler.invoke(constructor, editor, project);
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.RefactoringBulb;
  }

  private static boolean isNotEnumClass(@Nullable PsiClass psiClass) {
    return psiClass != null && !psiClass.isEnum();
  }
  
  @Nullable
  private static PsiMethod getConstructor(@Nullable PsiElement element) {
    PsiMethod method = RefactoringActionContextUtil.getJavaMethodHeader(element);
    return method != null && method.isConstructor() && 
           isNotEnumClass(method.getContainingClass()) ? method : null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}