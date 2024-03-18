// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.ReplaceConstructorWithFactoryAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class PublicConstructorInspection extends BaseInspection {

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return LocalQuickFix.from(new ReplaceConstructorWithFactoryAction());
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (((Boolean)infos[0]).booleanValue()) {
      return InspectionGadgetsBundle.message("public.default.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("public.constructor.problem.descriptor");
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorVisitor();
  }

  private static class PublicConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.isRecord()) {
        return;
      }
      if (SerializationUtils.isExternalizable(aClass)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty()) {
          return;
        }
      }
      registerMethodError(method, Boolean.FALSE);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface() || aClass.isEnum() || aClass.isRecord()) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.PUBLIC) || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length > 0) {
        return;
      }
      if (SerializationUtils.isExternalizable(aClass)) {
        return;
      }
      registerClassError(aClass, Boolean.TRUE);
    }
  }
}
