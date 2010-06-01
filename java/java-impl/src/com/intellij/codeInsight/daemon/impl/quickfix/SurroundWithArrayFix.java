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

/*
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class SurroundWithArrayFix extends PsiElementBaseIntentionAction {
  private final PsiCall myMethodCall;

  public SurroundWithArrayFix(final PsiCall methodCall) {
    myMethodCall = methodCall;
  }

  @NotNull
  public String getText() {
    return "Surround with array initialization";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    return getExpression(element) != null;
 }

  @Nullable
  protected PsiExpression getExpression(PsiElement element) {
    if (myMethodCall == null || !myMethodCall.isValid()) return null;
    final PsiElement method = myMethodCall.resolveMethod();
    if (method != null) {
      final PsiMethod psiMethod = (PsiMethod)method;
      return checkMethod(element, psiMethod);
    } else if (myMethodCall instanceof PsiMethodCallExpression){
      final Collection<PsiElement> psiElements = TargetElementUtil.getInstance().getTargetCandidates(((PsiMethodCallExpression)myMethodCall).getMethodExpression());
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
            final PsiType expressionType = expression.getType();
            if (expressionType != null) {
              final PsiType componentType = ((PsiArrayType)paramType).getComponentType();
              if (expressionType.isAssignableFrom(componentType)) {
                return expression;
              }
              final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
              if (ArrayUtil.find(psiMethod.getTypeParameters(), psiClass) != -1) {
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

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiExpression expression = getExpression(file.findElementAt(editor.getCaretModel().getOffset()));
    assert expression != null;
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getArrayCreation(expression), file);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression.replace(toReplace));
  }

  @NonNls
  private static String getArrayCreation(@NotNull PsiExpression expression) {
    final PsiType expressionType = expression.getType();
    assert expressionType != null;
    return "new " + expressionType.getCanonicalText() + "[]{" + expression.getText()+ "}";
  }

  public boolean startInWriteAction() {
    return true;
  }
}