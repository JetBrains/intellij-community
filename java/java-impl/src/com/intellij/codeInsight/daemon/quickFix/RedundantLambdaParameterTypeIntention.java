/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class RedundantLambdaParameterTypeIntention extends PsiElementBaseIntentionAction {
  public static final Logger LOG = Logger.getInstance("#" + RedundantLambdaParameterTypeIntention.class.getName());

  @NotNull
  @Override
  public String getFamilyName() {
    return "Remove redundant types";
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiParameterList parameterList = PsiTreeUtil.getParentOfType(element, PsiParameterList.class);
    if (parameterList == null) return false;
    final PsiElement parent = parameterList.getParent();
    if (!(parent instanceof PsiLambdaExpression)) return false;
    final PsiLambdaExpression expression = (PsiLambdaExpression)parent;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      if (parameter.getTypeElement() == null) return false;
    }
    if (parameters.length == 0) return false;
    final PsiType functionalInterfaceType = LambdaUtil.getFunctionalInterfaceType(expression, true);
    if (functionalInterfaceType != null) {
      final PsiElement lambdaParent = expression.getParent();
      if (lambdaParent instanceof PsiExpressionList) {
        final PsiElement gParent = lambdaParent.getParent();
        if (gParent instanceof PsiCallExpression && ((PsiCallExpression)gParent).getTypeArguments().length == 0) {
          final JavaResolveResult resolveResult = ((PsiCallExpression)gParent).resolveMethodGenerics();
          final PsiMethod method = (PsiMethod)resolveResult.getElement();
          if (method == null) return false;
          final int idx = LambdaUtil.getLambdaIdx((PsiExpressionList)lambdaParent, expression);
          if (idx < 0) return false;

          final PsiTypeParameter[] typeParameters = method.getTypeParameters();
          final PsiExpression[] arguments = ((PsiExpressionList)lambdaParent).getExpressions();
          final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
          arguments[idx] = javaPsiFacade.getElementFactory().createExpressionFromText(
            "(" + StringUtil.join(expression.getParameterList().getParameters(), new Function<PsiParameter, String>() {
              @Override
              public String fun(PsiParameter parameter) {
                return parameter.getName();
              }
            }, ", ") + ") -> {}", expression);
          final PsiParameter[] methodParams = method.getParameterList().getParameters();
          final PsiSubstitutor substitutor = javaPsiFacade.getResolveHelper()
            .inferTypeArguments(typeParameters, methodParams, arguments, ((MethodCandidateInfo)resolveResult).getSiteSubstitutor(),
                                gParent, DefaultParameterTypeInferencePolicy.INSTANCE);

          for (PsiTypeParameter parameter : typeParameters) {
            final PsiType psiType = substitutor.substitute(parameter);
            if (psiType == null || dependsOnTypeParams(psiType, expression, parameter)) return false;
          }
          
          
          final PsiType paramType;
          if (idx < methodParams.length) {
            paramType = methodParams[idx].getType();
          }
          else {
            final PsiParameter lastParam = methodParams[methodParams.length - 1];
            if (!lastParam.isVarArgs()) return false;
            paramType = ((PsiEllipsisType)lastParam.getType()).getComponentType();
          }
          return functionalInterfaceType.isAssignableFrom(substitutor.substitute(paramType));
        }
      }
      if (!LambdaUtil.isLambdaFullyInferred(expression, functionalInterfaceType)) {
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
    removeTypes(lambdaExpression);
  }

  private static void removeTypes(PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null) {
      final PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
      final String text;
      if (parameters.length == 1) {
        text = parameters[0].getName();
      }
      else {
        text = "(" + StringUtil.join(parameters, new Function<PsiParameter, String>() {
          @Override
          public String fun(PsiParameter parameter) {
            return parameter.getName();
          }
        }, ", ") + ")";
      }
      final PsiLambdaExpression expression = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(lambdaExpression.getProject())
        .createExpressionFromText(text + "->{}", lambdaExpression);
      lambdaExpression.getParameterList().replace(expression.getParameterList());
    }
  }

  private static boolean dependsOnTypeParams(PsiType type,
                                             PsiLambdaExpression expr,
                                             PsiTypeParameter param2Check) {
    return LambdaUtil.depends(type, new LambdaUtil.TypeParamsChecker(expr, PsiUtil
      .resolveGenericsClassInType(LambdaUtil.getFunctionalInterfaceType(expr, false)).getElement()), param2Check);
  }
}
