/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


/**
 * @author Dmitry Batkovich
 */
public class ReplaceWithMapPutIfAbsentFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiMethodCallExpression> myPutExpressionPointer;

  public ReplaceWithMapPutIfAbsentFix(PsiMethodCallExpression putExpression) {
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(putExpression.getProject());
    myPutExpressionPointer = smartPointerManager.createSmartPsiElementPointer(putExpression);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement conditionalOperator = descriptor.getPsiElement();
    if (conditionalOperator == null) return;
    final ConditionalOperatorHelper operatorHelper = getHelper(conditionalOperator);

    final PsiMethodCallExpression putExpression = myPutExpressionPointer.getElement();
    if (putExpression == null) return;

    PsiElement putContainingBranch = null;
    for (PsiElement branch : operatorHelper.getBranches(conditionalOperator)) {
      if (branch != null && PsiTreeUtil.isAncestor(branch, putExpression, false)) {
        putContainingBranch = branch;
        break;
      }
    }
    if (putContainingBranch == null) return;

    final PsiExpression[] arguments = putExpression.getArgumentList().getExpressions();
    final PsiElement qualifier = putExpression.getMethodExpression().getQualifier();
    if (qualifier == null) {
      return;
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    final PsiElement putContainingElement = operatorHelper.getPutContainingElement(putExpression);
    final Couple<String> boundText = getBoundText(putContainingElement, putExpression);

    final PsiStatement newStatement = elementFactory.createStatementFromText(boundText.getFirst() + qualifier.getText() + ".putIfAbsent"
                                                                             + "(" + arguments[0].getText() + "," +
                                                                             putExpression.getArgumentList().getExpressions()[1].getText() +
                                                                             ")" + boundText.getSecond(), conditionalOperator);
    conditionalOperator.replace(newStatement);
  }

  private static Couple<String> getBoundText(@NotNull PsiElement parent, @NotNull PsiElement child) {
    final TextRange childRange = child.getTextRange();
    final int parentStartOffset = parent.getTextRange().getStartOffset();
    final String parentText = parent.getText();
    return Couple.of(parentText.substring(0, childRange.getStartOffset() - parentStartOffset),
                     parentText.substring(childRange.getEndOffset() - parentStartOffset));
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("java.8.collections.api.inspection.fix.family.name");
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return QuickFixBundle.message("java.8.collections.api.inspection.fix.text", "putIfAbsent");
  }

  private static ConditionalOperatorHelper getHelper(PsiElement element) {
    return element instanceof PsiConditionalExpression ? new ConditionalExpressionHelper() : new IfStatementHelper();
  }

  interface ConditionalOperatorHelper {
    @NotNull
    PsiElement[] getBranches(PsiElement element);

    @NotNull
    PsiElement getPutContainingElement(PsiElement putElement);
  }

  private static class ConditionalExpressionHelper implements ConditionalOperatorHelper {
    @NotNull
    @Override
    public PsiElement[] getBranches(PsiElement element) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
      return new PsiElement[]{conditionalExpression.getThenExpression(), conditionalExpression.getElseExpression()};
    }

    @NotNull
    @Override
    public PsiElement getPutContainingElement(PsiElement putElement) {
      for (PsiElement element : getBranches(PsiTreeUtil.getParentOfType(putElement, PsiConditionalExpression.class))) {
        if (PsiTreeUtil.isAncestor(element, putElement, false)) {
          return element;
        }
      }
      throw new AssertionError();
    }
  }

  private static class IfStatementHelper implements ConditionalOperatorHelper {
    @NotNull
    @Override
    public PsiElement[] getBranches(PsiElement element) {
      final PsiIfStatement ifStatement = (PsiIfStatement)element;
      return new PsiElement[]{ifStatement.getThenBranch(), ifStatement.getElseBranch()};
    }

    @NotNull
    @Override
    public PsiElement getPutContainingElement(PsiElement putElement) {
      return PsiTreeUtil.getParentOfType(putElement, PsiStatement.class);
    }
  }
}
