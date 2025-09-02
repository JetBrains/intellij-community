/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public final class UnnecessarySuperConstructorInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public @NotNull String getID() {
    return "UnnecessaryCallToSuper";
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.super.constructor.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessarySuperConstructorFix();
  }

  private static class UnnecessarySuperConstructorFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.super.constructor.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement superCall, @NotNull ModPsiUpdater updater) {
      final PsiElement callStatement = superCall.getParent();
      assert callStatement != null;
      callStatement.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarySuperConstructorVisitor();
  }

  private static class UnnecessarySuperConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      if (methodExpression.isQualified() || !JavaKeywords.SUPER.equals(methodExpression.getReferenceName())) {
        return;
      }
      if (!call.getArgumentList().isEmpty()) {
        return;
      }
      registerError(call);
    }
  }
}