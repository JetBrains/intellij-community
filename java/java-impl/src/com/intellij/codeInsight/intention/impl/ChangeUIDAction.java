// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;


import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.Random;


public class ChangeUIDAction extends PsiElementBaseIntentionAction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("change.uid.action.name");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    Application application = ApplicationManager.getApplication();
    Random random = application.isUnitTestMode() ? new Random(42) : new SecureRandom();
    PsiExpression newInitializer = factory.createExpressionFromText(Long.toString(random.nextLong()) + "L", null);
    PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      new CommentTracker().replaceAndRestoreComments(initializer, newInitializer);
    } else {
      field.setInitializer(newInitializer);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) return false;
    if (!field.getType().equals(PsiType.LONG)) return false;
    return "serialVersionUID".equals(field.getName());
  }
}
