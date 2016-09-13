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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ComparatorCombinatorsInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiType type = lambda.getFunctionalInterfaceType();
        if(type instanceof PsiClassType && ((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          PsiElement body = lambda.getBody();
          if(body instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)body;
            if(MethodUtils.isCompareToCall(methodCall)) {
              PsiExpression left = methodCall.getMethodExpression().getQualifierExpression();
              PsiExpression right = methodCall.getArgumentList().getExpressions()[0];
              if(left instanceof PsiMethodCallExpression && right instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression leftCall = (PsiMethodCallExpression)left;
                PsiMethodCallExpression rightCall = (PsiMethodCallExpression)right;
                if(leftCall.getArgumentList().getExpressions().length == 0 &&
                   rightCall.getArgumentList().getExpressions().length == 0) {
                  PsiMethod leftMethod = leftCall.resolveMethod();
                  PsiMethod rightMethod = rightCall.resolveMethod();
                  if(leftMethod != null && rightMethod != null && leftMethod == rightMethod) {
                    if (areLambdaParameters(lambda, leftCall.getMethodExpression().getQualifierExpression(),
                                            rightCall.getMethodExpression().getQualifierExpression())) {
                      //noinspection DialogTitleCapitalization
                      holder.registerProblem(lambda, "Can be replaced with Comparator.comparing", new ReplaceWithComparatorFix());
                    }
                  }
                }
              }
            }
          }
        }
      }
    };
  }

  private static boolean areLambdaParameters(PsiLambdaExpression lambda, PsiExpression left, PsiExpression right) {
    PsiParameter[] parameters = lambda.getParameterList().getParameters();
    return left instanceof PsiReferenceExpression &&
           right instanceof PsiReferenceExpression &&
           ((PsiReferenceExpression)left).resolve() == parameters[0] &&
           ((PsiReferenceExpression)right).resolve() == parameters[1];
  }

  static class ReplaceWithComparatorFix implements LocalQuickFix {

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with Comparator.comparing";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiLambdaExpression)) return;
      PsiLambdaExpression lambda = (PsiLambdaExpression)element;
      PsiElement body = lambda.getBody();
      if (!(body instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)body;
      if (!MethodUtils.isCompareToCall(methodCall)) return;
      PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      if (!(qualifier instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression call = (PsiMethodCallExpression)qualifier;
      if (call.getArgumentList().getExpressions().length != 0) return;
      PsiMethod method = call.resolveMethod();
      if (method == null) return;
      PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression replacement =
        factory.createExpressionFromText("java.util.Comparator.comparing(" + methodClass.getQualifiedName() + "::" + method.getName() + ")",
                                         element);
      PsiElement result = lambda.replace(replacement);
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }
  }
}
