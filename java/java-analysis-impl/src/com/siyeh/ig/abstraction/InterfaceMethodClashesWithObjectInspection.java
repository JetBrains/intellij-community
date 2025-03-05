// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.abstraction;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class InterfaceMethodClashesWithObjectInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("interface.clashes.with.object.class.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InterfaceClashesWithObjectClassVisitor();
  }

  private static class InterfaceClashesWithObjectClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.getParameterList().isEmpty()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !aClass.isInterface()) {
        return;
      }
      final @NonNls String name = method.getName();
      if ("clone".equals(name) && !(method.getReturnType() instanceof PsiClassType)) {
        registerMethodError(method);
      }
      else if ("finalize".equals(name) && !PsiTypes.voidType().equals(method.getReturnType())) {
        registerMethodError(method);
      }
    }
  }
}
