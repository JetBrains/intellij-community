// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.ReplaceConstructorWithFactoryAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class PublicConstructorInspection extends BaseInspection {

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return LocalQuickFix.from(new ReplaceConstructorWithFactoryAction());
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    boolean defaultConstructor = ((Boolean)infos[0]).booleanValue();
    return defaultConstructor
           ? InspectionGadgetsBundle.message("public.default.constructor.problem.descriptor")
           : InspectionGadgetsBundle.message("public.constructor.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorVisitor();
  }

  private static class PublicConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface() || aClass.isEnum() || aClass.isRecord()) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
          continue;
        }
        if (SerializationUtils.isExternalizable(aClass) && constructor.getParameterList().isEmpty()) {
          continue;
        }
        registerMethodError(constructor, Boolean.FALSE);
      }
      if (constructors.length == 0 && aClass.hasModifierProperty(PsiModifier.PUBLIC) && !SerializationUtils.isExternalizable(aClass)) {
        registerClassError(aClass, Boolean.TRUE);
      }
    }
  }
}
