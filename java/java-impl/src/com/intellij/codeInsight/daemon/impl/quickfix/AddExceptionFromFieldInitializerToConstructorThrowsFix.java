// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class AddExceptionFromFieldInitializerToConstructorThrowsFix extends PsiUpdateModCommandAction<PsiElement> {
  private final static Logger LOG = Logger.getInstance(AddExceptionFromFieldInitializerToConstructorThrowsFix.class);

  public AddExceptionFromFieldInitializerToConstructorThrowsFix(PsiElement element) {
    super(element);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    final NavigatablePsiElement maybeField =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiFunctionalExpression.class, PsiField.class);
    if (!(maybeField instanceof PsiField field)) return null;
    if (field.hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null ||
        containingClass instanceof PsiAnonymousClass ||
        containingClass.isInterface()) {
      return null;
    }
    final List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(field);
    if (exceptions.isEmpty()) {
      return null;
    }
    final PsiMethod[] existedConstructors = containingClass.getConstructors();
    return Presentation.of(QuickFixBundle.message("add.exception.from.field.initializer.to.constructor.throws.text", existedConstructors.length));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final NavigatablePsiElement e =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiFunctionalExpression.class, PsiField.class);
    if (e instanceof PsiField field) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
          AddDefaultConstructorFix.addDefaultConstructor(aClass);
          constructors = aClass.getConstructors();
          LOG.assertTrue(constructors.length != 0);
        }

        Set<PsiClassType> unhandledExceptions = new HashSet<>(ExceptionUtil.getUnhandledExceptions(e));
        for (PsiMethod constructor : constructors) {
          AddExceptionToThrowsFix.processMethod(context.project(), constructor, unhandledExceptions);
        }
      }
    }
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.exception.from.field.initializer.to.constructor.throws.family.text");
  }
}
