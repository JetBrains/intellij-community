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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class RedundantLambdaParameterTypeInspection extends BaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + RedundantLambdaParameterTypeInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Redundant lambda parameter type";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "RedundantLambdaParameterType";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        final PsiParameter[] parameters = expression.getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
          if (parameter.getTypeElement() == null) return;
        }
        if (parameters.length == 0) return;
        final PsiType functionalInterfaceType = LambdaUtil.getFunctionalInterfaceType(expression, false);
        if (functionalInterfaceType != null) {
          if (!LambdaUtil.isLambdaFullyInferred(expression, functionalInterfaceType)) {
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionList) {
              final PsiElement gParent = parent.getParent();
              if (gParent instanceof PsiCallExpression && ((PsiCallExpression)gParent).getTypeArguments().length == 0) {
                final PsiMethod method = ((PsiCallExpression)gParent).resolveMethod();
                if (method == null) return;
                final int idx = LambdaUtil.getLambdaIdx((PsiExpressionList)parent, expression);
                if (idx < 0) return;

                final PsiTypeParameter[] typeParameters = method.getTypeParameters();
                final PsiExpression[] arguments = ((PsiExpressionList)parent).getExpressions();
                final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(holder.getProject());
                arguments[idx] = javaPsiFacade.getElementFactory().createExpressionFromText("null", expression);
                final PsiSubstitutor substitutor = javaPsiFacade.getResolveHelper()
                  .inferTypeArguments(typeParameters, method.getParameterList().getParameters(), arguments, PsiSubstitutor.EMPTY,
                                      gParent, DefaultParameterTypeInferencePolicy.INSTANCE);

                for (PsiTypeParameter parameter : typeParameters) {
                  final PsiType psiType = substitutor.substitute(parameter);
                  if (psiType == null || LambdaUtil.dependsOnTypeParams(psiType, expression, parameter)) return;
                }
              }
            }
          }
          holder.registerProblem(expression.getParameterList(), "Redundant parameter type declarations",
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL, new RemoveTypeDeclarationsFix());
        }
      }
    };
  }

  private static class RemoveTypeDeclarationsFix implements LocalQuickFix, HighPriorityAction {

    @NotNull
    @Override
    public String getName() {
      return "Remove redundant types";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
        removeTypes(lambdaExpression);
      }
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
  }
}
