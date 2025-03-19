// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class OptionalUsedAsFieldOrParameterTypeInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiTypeElement typeElement = (PsiTypeElement)infos[0];
    final PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiField field) {
      return InspectionGadgetsBundle.message("optional.used.as.field.type.problem.descriptor", field.getName());
    }
    else if (parent instanceof PsiParameter parameter) {
      return InspectionGadgetsBundle.message("optional.used.as.parameter.type.problem.descriptor", parameter.getName());
    }
    throw new AssertionError();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionUsedAsFieldOrParameterTypeVisitor();
  }

  private static class OptionUsedAsFieldOrParameterTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      checkTypeElement(field.getTypeElement());
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      final PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod method)) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      checkTypeElement(parameter.getTypeElement());
    }

    private void checkTypeElement(PsiTypeElement typeElement) {
      if (typeElement == null || !TypeUtils.isOptional(typeElement.getType())) {
        return;
      }
      registerError(typeElement, typeElement);
    }
  }
}
