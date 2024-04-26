// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ConvertToVarargsMethodFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ProblematicVarargsMethodOverrideInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("problematic.varargs.method.override.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ConvertToVarargsMethodFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonVarargsMethodOverridesVarArgsMethodVisitor();
  }

  private static class NonVarargsMethodOverridesVarArgsMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return;
      }
      final PsiParameter parameter = parameters[parameters.length - 1];
      final PsiType type = parameter.getType();
      if (!(type instanceof PsiArrayType) || type instanceof PsiEllipsisType) {
        return;
      }
      final PsiMethod[] superMethods = method.findDeepestSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        if (superMethod.isVarArgs()) {
          final PsiElement nameIdentifier = method.getNameIdentifier();
          if (nameIdentifier != null) {
            registerErrorAtOffset(method, nameIdentifier.getStartOffsetInParent(), nameIdentifier.getTextLength());
          }
          return;
        }
      }
    }
  }
}
