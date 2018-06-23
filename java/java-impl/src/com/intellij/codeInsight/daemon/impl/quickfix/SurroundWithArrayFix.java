/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SurroundWithArrayFix extends PsiElementBaseIntentionAction {
  private final PsiCall myMethodCall;
  @Nullable private final PsiExpression myExpression;

  public SurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
    myMethodCall = methodCall;
    myExpression = expression;
  }

  @Override
  @NotNull
  public String getText() {
    return "Surround with array initialization";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    return getExpression(element) != null;
 }

  @Nullable
  protected PsiExpression getExpression(PsiElement element) {
    if (myMethodCall == null || !myMethodCall.isValid()) {
      return myExpression == null || !myExpression.isValid() ? null : myExpression;
    }
    final PsiMethod method = myMethodCall.resolveMethod();
    if (method != null) {
      return checkMethod(element, method);
    } else if (myMethodCall instanceof PsiMethodCallExpression){
      final Collection<PsiElement> psiElements = TargetElementUtil.getInstance()
        .getTargetCandidates(((PsiMethodCallExpression)myMethodCall).getMethodExpression());
      for (PsiElement psiElement : psiElements) {
        if (psiElement instanceof PsiMethod) {
          final PsiExpression expression = checkMethod(element, (PsiMethod)psiElement);
          if (expression != null) return expression;
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiExpression checkMethod(final PsiElement element, final PsiMethod psiMethod) {
    final PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = myMethodCall.getArgumentList();
    int idx = 0;
    for (PsiExpression expression : argumentList.getExpressions()) {
      if (element != null && PsiTreeUtil.isAncestor(expression, element, false)) {
        if (psiParameters.length > idx) {
          final PsiType paramType = psiParameters[idx].getType();
          if (paramType instanceof PsiArrayType) {
            final PsiType expressionType = TypeConversionUtil.erasure(expression.getType());
            if (expressionType != null && PsiTypesUtil.isDenotableType(expressionType, element) && expressionType != PsiType.NULL) {
              final PsiType componentType = ((PsiArrayType)paramType).getComponentType();
              if (TypeConversionUtil.isAssignable(componentType, expressionType)) {
                return expression;
              }
              final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
              if (ArrayUtilRt.find(psiMethod.getTypeParameters(), psiClass) != -1) {
                for (PsiClassType superType : psiClass.getSuperTypes()) {
                  if (TypeConversionUtil.isAssignable(superType, expressionType)) return expression;
                }
              }
            }
          }
        }
      }
      idx++;
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiExpression expression = getExpression(element);
    assert expression != null;
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getArrayCreation(expression), element);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression.replace(toReplace));
  }

  @NonNls
  private static String getArrayCreation(@NotNull PsiExpression expression) {
    final PsiType expressionType = expression.getType();
    assert expressionType != null;
    final PsiType arrayComponentType = TypeConversionUtil.erasure(expressionType);
    return "new " + arrayComponentType.getCanonicalText() + "[]{" + expression.getText()+ "}";
  }
}