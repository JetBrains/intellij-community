/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_BOOLEAN;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class NotExpressionPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {

  private static final Condition<? super PsiElement> IS_FUNCTION_RETURNING_BOOLEAN = e -> {
    if (!(e instanceof PsiMethodReferenceExpression methodRef)) return false;
    return DumbService.getInstance(e.getProject()).computeWithAlternativeResolveEnabled(
      () -> PsiTypes.booleanType().equals(LambdaUtil.getFunctionalInterfaceReturnType(methodRef)) &&
            LambdaRefactoringUtil.canConvertToLambda(methodRef) &&
            methodRef.advancedResolve(false) instanceof MethodCandidateInfo info && info.isApplicable());
  };
  private static final @NotNull PostfixTemplateExpressionSelector SELECTOR =
    selectorAllExpressionsWithCurrentOffset(Conditions.or(
      // Exclude negated expressions from possible options. If we have `!x` in context, 
      // then .not should be applied to whole `!x` producing `x`, rather than to `x` producing double negation
      Conditions.and(IS_BOOLEAN, e -> e instanceof PsiExpression expr && !BoolUtils.isNegated(expr)), IS_FUNCTION_RETURNING_BOOLEAN));

  public NotExpressionPostfixTemplate() {
    super(null, "not", "!expr", SELECTOR, null);
  }

  public NotExpressionPostfixTemplate(String alias) {
    super(alias, alias, "!expr", SELECTOR);
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    Project project = expression.getProject();
    DumbService.getInstance(project)
      .runWithAlternativeResolveEnabled(() -> {
        PsiExpression expr = (PsiExpression)expression;
        PsiLambdaExpression lambda = null;
        if (expression instanceof PsiMethodReferenceExpression methodRef) {
          lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, false, true);
          if (lambda == null) {
            return;
          }
          expr = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
        }

        String negatedExpressionText = BoolUtils.getNegatedExpressionText(expr);
        PsiElement element = JavaPsiFacade.getElementFactory(project).createExpressionFromText(negatedExpressionText, expr);
        expr.replace(element);
        if (lambda != null) {
          LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda);
        }
      });
  }
}