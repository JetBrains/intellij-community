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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ConvertCompareToToEqualsIntention extends BaseElementAtCaretIntentionAction {
  public static final String TEXT = "Convert '.compareTo()' method to '.equals()' (may change semantics)";

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) {
      return;
    }
    final ResolveResult resolveResult = findCompareTo(element);
    assert resolveResult != null;
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final Pair<PsiExpression, PsiExpression> qualifierAndParameter = getQualifierAndParameter(resolveResult.getCompareToCall());
    final PsiExpression newExpression =
      elementFactory.createExpressionFromText(String.format((resolveResult.isEqEq() ? "" : "!") + "%s.equals(%s)", qualifierAndParameter.getFirst().getText(), qualifierAndParameter.getSecond().getText()), null);
    final PsiElement result = resolveResult.getBinaryExpression().replace(newExpression);

    editor.getCaretModel().moveToOffset(result.getTextOffset() + result.getTextLength());
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    return findCompareTo(element) != null;
  }

  private static Pair<PsiExpression, PsiExpression> getQualifierAndParameter(PsiMethodCallExpression methodCallExpression) {
    final PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
    assert qualifier != null;
    final PsiExpression parameter = methodCallExpression.getArgumentList().getExpressions()[0];
    return Pair.create(qualifier, parameter);
  }

  @Nullable
  private static ResolveResult findCompareTo(PsiElement element) {
    final PsiBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiBinaryExpression.class);
    if (binaryExpression == null) {
      return null;
    }
    final PsiJavaToken operationSign = binaryExpression.getOperationSign();
    boolean isEqEq;
    if (JavaTokenType.NE.equals(operationSign.getTokenType())) {
      isEqEq = false;
    } else if (JavaTokenType.EQEQ.equals(operationSign.getTokenType())) {
      isEqEq = true;
    } else {
      return null;
    }
    PsiMethodCallExpression compareToExpression = null;
    boolean hasZero = false;
    for (PsiExpression psiExpression : binaryExpression.getOperands()) {
      if (compareToExpression == null && detectCompareTo(psiExpression)) {
        compareToExpression = (PsiMethodCallExpression)psiExpression;
        continue;
      }
      if (!hasZero && detectZero(psiExpression)) {
        hasZero = true;
      }
    }
    if (!hasZero || compareToExpression == null) {
      return null;
    }
    getQualifierAndParameter(compareToExpression);
    return new ResolveResult(binaryExpression, compareToExpression, isEqEq);
  }

  private static boolean detectCompareTo(final @NotNull PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    if (methodCallExpression.getMethodExpression().getQualifierExpression() == null) {
      return false;
    }
    final PsiMethod psiMethod = methodCallExpression.resolveMethod();
    if (psiMethod == null || !"compareTo".equals(psiMethod.getName()) || psiMethod.getParameterList().getParametersCount() != 1) {
      return false;
    }
    if (methodCallExpression.getArgumentList().getExpressions().length != 1) {
      return false;
    }
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final PsiClass javaLangComparable = JavaPsiFacade.getInstance(expression.getProject()).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, GlobalSearchScope.allScope(
      expression.getProject()));
    if (javaLangComparable == null) {
      return false;
    }
    if (!containingClass.isInheritor(javaLangComparable, true)) {
      return false;
    }
    return true;
  }

  private static boolean detectZero(final @NotNull PsiExpression expression) {
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final Object value = ((PsiLiteralExpression)expression).getValue();
    return Comparing.equal(value, 0);
  }

  private static class ResolveResult {
    private final PsiBinaryExpression myBinaryExpression;
    private final PsiMethodCallExpression myCompareToCall;
    private final boolean myEqEq;

    private ResolveResult(PsiBinaryExpression binaryExpression, PsiMethodCallExpression compareToCall, boolean eqEq) {
      myBinaryExpression = binaryExpression;
      myCompareToCall = compareToCall;
      myEqEq = eqEq;
    }

    public PsiBinaryExpression getBinaryExpression() {
      return myBinaryExpression;
    }

    public PsiMethodCallExpression getCompareToCall() {
      return myCompareToCall;
    }

    public boolean isEqEq() {
      return myEqEq;
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return TEXT;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}