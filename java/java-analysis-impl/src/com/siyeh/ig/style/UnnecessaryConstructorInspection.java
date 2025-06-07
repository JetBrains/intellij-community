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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.VisibilityUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryConstructorInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAnnotations = false;

  @Override
  public @NotNull String getID() {
    return "RedundantNoArgConstructor";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAnnotations", InspectionGadgetsBundle.message(
        "unnecessary.constructor.annotation.option")));
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstructorVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryConstructorFix();
  }

  private static class UnnecessaryConstructorFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.constructor.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement nameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement constructor = nameIdentifier.getParent();
      assert constructor != null;
      constructor.delete();
    }
  }

  private class UnnecessaryConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length != 1) {
        return;
      }
      final PsiMethod constructor = constructors[0];
      final PsiIdentifier identifier = constructor.getNameIdentifier();
      if (!constructor.isPhysical() || identifier == null) {
        return;
      }
      if (!aClass.isEnum()) {
        String modifier = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
        if (!constructor.hasModifierProperty(modifier)) {
          return;
        }
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      if (!parameterList.isEmpty()) {
        return;
      }
      if (ignoreAnnotations) {
        final PsiModifierList modifierList = constructor.getModifierList();
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        if (annotations.length > 0) {
          return;
        }
      }
      final PsiReferenceList throwsList = constructor.getThrowsList();
      final PsiJavaCodeReferenceElement[] elements = throwsList.getReferenceElements();
      if (elements.length != 0) {
        return;
      }
      final PsiCodeBlock body = constructor.getBody();
      if (ControlFlowUtils.isEmptyCodeBlock(body) ||
          isSuperConstructorInvocationWithoutArguments(ControlFlowUtils.getOnlyStatementInBlock(body))) {
        registerError(identifier);
      }
    }

    private static boolean isSuperConstructorInvocationWithoutArguments(PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
        return false;
      }
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (!argumentList.isEmpty()) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      return JavaKeywords.SUPER.equals(methodExpression.getReferenceName());
    }
  }
}