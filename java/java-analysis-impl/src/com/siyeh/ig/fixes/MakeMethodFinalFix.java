// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNullElse;

public class MakeMethodFinalFix extends PsiUpdateModCommandQuickFix {

  private final String myMethodName;

  public MakeMethodFinalFix(String methodName) {
    myMethodName = methodName;
  }

  @NotNull
  @Override
  public String getName() {
    return InspectionGadgetsBundle.message("make.method.final.fix.name", myMethodName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("make.method.final.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
    PsiElement element = startElement.getParent();
    PsiMethod method = findMethodToFix(element);
    if (method != null) {
      method = updater.getWritable(method);
      method.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      if (method.getContainingFile() != element.getContainingFile()) {
        updater.moveCaretTo(requireNonNullElse(method.getNameIdentifier(), method));
      }
    }
  }

  private static @Nullable PsiMethod findMethodToFix(PsiElement element) {
    if (element instanceof PsiMethod method) {
      return method;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiMethodCallExpression methodCall) {
      return methodCall.resolveMethod();
    }
    return null;
  }
}
