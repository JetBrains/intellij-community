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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


/**
 * @author Dmitry Batkovich
 */
public class ReplaceWithMapPutIfAbsentFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiMethodCallExpression> myPutExpressionPointer;
  private final boolean myLazyValueComputation;

  public ReplaceWithMapPutIfAbsentFix(PsiMethodCallExpression putExpression, boolean lazyValueComputation) {
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(putExpression.getProject());
    myPutExpressionPointer = smartPointerManager.createSmartPsiElementPointer(putExpression);
    myLazyValueComputation = lazyValueComputation;
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
    final String methodName = getMethodName();

    final PsiElement putContainingElement = operatorHelper.getPutContainingElement(putExpression);
    final Couple<String> boundText = getBoundText(putContainingElement, putExpression);

    final PsiStatement newStatement = elementFactory.createStatementFromText(boundText.getFirst() + qualifier.getText() + "." + methodName
                                                                             + "(" + arguments[0].getText() + "," +
                                                                             createValueArgument(arguments[0],
                                                                                                 putExpression,
                                                                                                 putContainingBranch).getText() +
                                                                             ")" + boundText.getSecond(), conditionalOperator);
    conditionalOperator.replace(newStatement);
  }

  private PsiExpression createValueArgument(PsiExpression key, PsiMethodCallExpression putExpression, PsiElement putBranch) {
    if (!myLazyValueComputation) {
      return putExpression.getArgumentList().getExpressions()[1];
    }
    final String parameterName = suggestLambdaParameterName(key);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(key.getProject());
    if (key instanceof PsiReferenceExpression) {
      final PsiElement resolvedReference = ((PsiReferenceExpression)key).resolve();
      if (resolvedReference instanceof PsiVariable) {
        final PsiExpression newVariableReference = elementFactory.createExpressionFromText(parameterName, key.getContext());
        for (PsiReference reference : ReferencesSearch.search(resolvedReference, new LocalSearchScope(putBranch)).findAll()) {
          if (reference instanceof PsiJavaCodeReferenceElement &&
              resolvedReference.isEquivalentTo(reference.resolve())) {
            ((PsiJavaCodeReferenceElement)reference).replace(newVariableReference);
          }
        }
      }
    }
    final PsiElement lambdaBody = replaceLastStatementForLambda(putBranch, putExpression);
    final String lambdaExpression = "(" + parameterName + ") -> " + lambdaBody.getText();
    return elementFactory.createExpressionFromText(lambdaExpression, null);
  }

  private static Couple<String> getBoundText(@NotNull PsiElement parent, @NotNull PsiElement child) {
    final TextRange childRange = child.getTextRange();
    final int parentStartOffset = parent.getTextRange().getStartOffset();
    final String parentText = parent.getText();
    return Couple.of(parentText.substring(0, childRange.getStartOffset() - parentStartOffset),
                     parentText.substring(childRange.getEndOffset() - parentStartOffset));
  }

  @NotNull
  private static PsiElement replaceLastStatementForLambda(PsiElement putBranch, PsiMethodCallExpression putExpression) {
    if (putBranch instanceof PsiBlockStatement) {
      final PsiStatement[] statements = ((PsiBlockStatement)putBranch).getCodeBlock().getStatements();
      if (statements.length >= 2) {
        final PsiStatement putStatement = statements[statements.length - 1];
        final PsiExpression valueArgument = putExpression.getArgumentList().getExpressions()[1];
        putStatement.replace(
          JavaPsiFacade.getElementFactory(putBranch.getProject())
            .createStatementFromText("return " + valueArgument.getText() + ";", putBranch));
        return putBranch;
      }
    }
    return putExpression.getArgumentList().getExpressions()[1];
  }

  @NotNull
  private static String suggestLambdaParameterName(PsiExpression keyExpression) {
    final PsiType type = keyExpression.getType();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(keyExpression.getProject());
    final String name = codeStyleManager.suggestUniqueVariableName(
      codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type).names[0], keyExpression, false);
    return new UniqueNameGenerator().generateUniqueName(name);
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
    return QuickFixBundle.message("java.8.collections.api.inspection.fix.text", getMethodName());
  }

  @NotNull
  private String getMethodName() {
    return myLazyValueComputation ? "computeIfAbsent" : "putIfAbsent";
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
