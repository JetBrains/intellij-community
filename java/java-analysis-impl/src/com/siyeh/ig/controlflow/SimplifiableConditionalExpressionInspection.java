/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.style.ConditionalExpressionGenerator;
import com.siyeh.ig.style.ConditionalModel;
import org.jetbrains.annotations.NotNull;

public final class SimplifiableConditionalExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final String replacement = (String)infos[0];
    final String orig = (String)infos[1];
    return InspectionGadgetsBundle.message("simplifiable.conditional.expression.problem.descriptor", replacement, orig);
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new SimplifiableConditionalFix();
  }

  private static class SimplifiableConditionalFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiConditionalExpression expression = ObjectUtils.tryCast(startElement.getParent(), PsiConditionalExpression.class);
      if (expression == null) return;
      ConditionalModel model = ConditionalModel.from(expression);
      if (model == null) return;
      ConditionalExpressionGenerator generator = ConditionalExpressionGenerator.from(model);
      if (generator == null || generator.getTokenType().equals("?:")) return;
      CommentTracker tracker = new CommentTracker();
      final String newExpression = generator.generate(tracker);
      PsiReplacementUtil.replaceExpression(expression, newExpression, tracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableConditionalExpressionVisitor();
  }

  private static class SimplifiableConditionalExpressionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      ConditionalModel model = ConditionalModel.from(expression);
      if (model == null) return;
      ConditionalExpressionGenerator generator = ConditionalExpressionGenerator.from(model);
      if (generator == null || generator.getTokenType().equals("?:")) return;
      if (PsiTypes.booleanType().equals(model.getType()) &&
          ExpressionUtils.nonStructuralChildren(expression).anyMatch(ExpressionUtils::isNullLiteral)) {
        // Something like Boolean res = cond1 ? true : cond2 ? false : null
        // While warning is technically correct, it masks more serious "possible NPE" problem
        // and the applied fix makes it more confusing
        return;
      }
      PsiExpression replacement = generator.getReplacement();
      if (replacement != null) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiLambdaExpression &&
            !LambdaUtil.isSafeLambdaBodyReplacement((PsiLambdaExpression)parent, () -> replacement)) {
          return;
        }
      }
      registerError(expression.getCondition(), generator.generate(new CommentTracker()), expression.getText());
    }
  }
}