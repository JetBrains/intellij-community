/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.finalization;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FinalizeNotProtectedInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finalize.not.declared.protected.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinalizeDeclaredProtectedVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ProtectedFinalizeFix();
  }

  private static class ProtectedFinalizeFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("change.modifier.quickfix", PsiModifier.PROTECTED);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement methodName, @NotNull ModPsiUpdater updater) {
      final PsiMethod method = (PsiMethod)methodName.getParent();
      Objects.requireNonNull(method).getModifierList().setModifierProperty(PsiModifier.PROTECTED, true);
    }
  }

  private static class FinalizeDeclaredProtectedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!MethodUtils.isFinalize(method)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.isInterface()) {
        return;
      }
      for (PsiMethod superMethod : method.findSuperMethods()) {
        if (superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          return;
        }
      }
      registerMethodError(method);
    }
  }
}