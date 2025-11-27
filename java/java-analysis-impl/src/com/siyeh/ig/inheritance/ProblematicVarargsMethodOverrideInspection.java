// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ConvertToVarargsMethodFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ProblematicVarargsMethodOverrideInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("problematic.varargs.method.override.problem.descriptor");
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new ConvertToVarargsMethodFix();
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new NonVarargsMethodOverridesVarArgsMethodVisitor();
  }

  private static class NonVarargsMethodOverridesVarArgsMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 0) {
        return;
      }
      final PsiParameter parameter = parameters[parameters.length - 1];
      final PsiType type = parameter.getType();
      if (!(type instanceof PsiArrayType) || type instanceof PsiEllipsisType) {
        return;
      }
      for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
        if (superMethod.isVarArgs()) {
          final PsiElement nameIdentifier = method.getNameIdentifier();
          if (nameIdentifier != null) {
            registerMethodError(method);
          }
          return;
        }
      }
    }
  }
}
