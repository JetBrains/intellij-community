// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;


import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Random;


public final class ChangeUIDAction extends PsiUpdateModCommandAction<PsiField> {
  public ChangeUIDAction() {
    super(PsiField.class);
  }
  
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("change.uid.action.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiField field, @NotNull ModPsiUpdater updater) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    Application application = ApplicationManager.getApplication();
    Random random = application.isUnitTestMode() ? new Random(42) : new SecureRandom();
    PsiExpression newInitializer = factory.createExpressionFromText(random.nextLong() + "L", null);
    PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      new CommentTracker().replaceAndRestoreComments(initializer, newInitializer);
    } else {
      field.setInitializer(newInitializer);
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiField field) {
    if (!field.getType().equals(PsiTypes.longType())) return null;
    if (!"serialVersionUID".equals(field.getName())) return null;
    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      boolean initializersHasReferencesToField = StreamEx.of(aClass.getInitializers())
        .flatMap(initializer -> StreamEx.ofTree((PsiElement)initializer, el -> StreamEx.of(el.getChildren())))
        .select(PsiReferenceExpression.class)
        .anyMatch(el -> ExpressionUtils.isReferenceTo(el, field));
      if (initializersHasReferencesToField) {
        return null;
      }
    }
    return Presentation.of(getFamilyName());
  }
}
