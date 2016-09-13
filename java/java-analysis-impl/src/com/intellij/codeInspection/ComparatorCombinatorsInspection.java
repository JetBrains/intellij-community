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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

public class ComparatorCombinatorsInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @SuppressWarnings("DialogTitleCapitalization")
      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiType type = lambda.getFunctionalInterfaceType();
        if(type instanceof PsiClassType && ((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          PsiElement body = lambda.getBody();
          if(body instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)body;
            PsiExpression[] args = methodCall.getArgumentList().getExpressions();
            if(args.length == 1 && MethodUtils.isCompareToCall(methodCall)) {
              PsiExpression left = methodCall.getMethodExpression().getQualifierExpression();
              PsiExpression right = args[0];
              if(areEquivalent(lambda.getParameterList().getParameters(), left, right)) {
                holder.registerProblem(lambda, "Can be replaced with Comparator.comparing", new ReplaceWithComparatorFix("comparing"));
              }
            } else {
              PsiMethod method = methodCall.resolveMethod();
              if(args.length == 2 && method != null && method.getName().equals("compare")) {
                PsiClass compareClass = method.getContainingClass();
                if(compareClass != null) {
                  String replacementMethodName;
                  if(CommonClassNames.JAVA_LANG_DOUBLE.equals(compareClass.getQualifiedName())) {
                    replacementMethodName = "comparingDouble";
                  }
                  else if(CommonClassNames.JAVA_LANG_INTEGER.equals(compareClass.getQualifiedName())) {
                    replacementMethodName = "comparingInt";
                  }
                  else if(CommonClassNames.JAVA_LANG_LONG.equals(compareClass.getQualifiedName())) {
                    replacementMethodName = "comparingLong";
                  }
                  else return;
                  if(areEquivalent(lambda.getParameterList().getParameters(), args[0], args[1])) {
                    holder.registerProblem(lambda, "Can be replaced with Comparator." + replacementMethodName,
                                           new ReplaceWithComparatorFix(replacementMethodName));
                  }
                }
              }
            }
          }
        }
      }
    };
  }

  private static boolean areEquivalent(PsiParameter[] parameters, PsiExpression left, PsiExpression right) {
    if(left == null && right == null) return true;
    if(left instanceof PsiMethodCallExpression && right instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression leftCall = (PsiMethodCallExpression)left;
      PsiMethodCallExpression rightCall = (PsiMethodCallExpression)right;
      PsiMethod leftMethod = leftCall.resolveMethod();
      PsiMethod rightMethod = rightCall.resolveMethod();
      if(leftMethod == null || leftMethod != rightMethod) return false;
      PsiExpression[] leftArgs = leftCall.getArgumentList().getExpressions();
      PsiExpression[] rightArgs = rightCall.getArgumentList().getExpressions();
      return leftArgs.length == rightArgs.length &&
             areEquivalent(parameters, leftCall.getMethodExpression(), rightCall.getMethodExpression()) &&
             !StreamEx.zip(leftArgs, rightArgs, (leftArg, rightArg) -> areEquivalent(parameters, leftArg, rightArg)).has(Boolean.FALSE);
    }
    if(left instanceof PsiReferenceExpression && right instanceof PsiReferenceExpression) {
      PsiReferenceExpression leftRef = (PsiReferenceExpression)left;
      PsiReferenceExpression rightRef = (PsiReferenceExpression)right;
      PsiElement leftElement = leftRef.resolve();
      PsiElement rightElement = rightRef.resolve();
      if(leftElement instanceof PsiParameter) {
        return leftElement == parameters[0] && rightElement == parameters[1];
      }
      if(leftElement != rightElement) return false;
      PsiType[] leftTypes = leftRef.getTypeParameters();
      PsiType[] rightTypes = leftRef.getTypeParameters();
      return leftTypes.length == rightTypes.length && !StreamEx.zip(leftTypes, rightTypes, Objects::equals).has(Boolean.FALSE)
        && areEquivalent(parameters, leftRef.getQualifierExpression(), rightRef.getQualifierExpression());
    }
    if(left instanceof PsiTypeCastExpression && right instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression leftCast = (PsiTypeCastExpression)left;
      PsiTypeCastExpression rightCast = (PsiTypeCastExpression)right;
      return leftCast.getCastType() == rightCast.getCastType() &&
             areEquivalent(parameters, leftCast.getOperand(), rightCast.getOperand());
    }
    return false;
  }

  static class ReplaceWithComparatorFix implements LocalQuickFix {
    private final String myMethodName;

    public ReplaceWithComparatorFix(String methodName) {
      myMethodName = methodName;
    }

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
      return "Replace with Comparator." + myMethodName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiLambdaExpression)) return;
      PsiLambdaExpression lambda = (PsiLambdaExpression)element;
      PsiElement body = lambda.getBody();
      if (!(body instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)body;
      PsiExpression keyExtractor = null;
      String methodName = null;
      if (MethodUtils.isCompareToCall(methodCall)) {
        methodName = "comparing";
        keyExtractor = methodCall.getMethodExpression().getQualifierExpression();
      } else {
        PsiMethod method = methodCall.resolveMethod();
        if(method != null && method.getName().equals("compare")) {
          PsiClass containingClass = method.getContainingClass();
          if(containingClass != null) {
            String className = containingClass.getQualifiedName();
            if(className != null) {
              PsiExpression[] args = methodCall.getArgumentList().getExpressions();
              if(args.length != 2) return;
              keyExtractor = args[0];
              switch (className) {
                case CommonClassNames.JAVA_LANG_LONG:
                  methodName = "comparingLong";
                  break;
                case CommonClassNames.JAVA_LANG_INTEGER:
                  methodName = "comparingInt";
                  break;
                case CommonClassNames.JAVA_LANG_DOUBLE:
                  methodName = "comparingDouble";
                  break;
                default:
                  return;
              }
            }
          }
        }
      }
      if(methodName == null || keyExtractor == null) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      PsiParameter parameter = lambda.getParameterList().getParameters()[0];
      String parameterName = parameter.getName();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression replacement = factory
        .createExpressionFromText("java.util.Comparator." + methodName + "(" + parameterName + " -> " + keyExtractor.getText() + ")",
                                  element);
      PsiElement result = lambda.replace(replacement);
      normalizeLambda(((PsiMethodCallExpression)result).getArgumentList().getExpressions()[0], factory);
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }

    private static void normalizeLambda(PsiExpression expression, PsiElementFactory factory) {
      if(!(expression instanceof PsiLambdaExpression)) return;
      PsiLambdaExpression lambda = (PsiLambdaExpression)expression;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      PsiElement body = lambda.getBody();
      if(body == null) return;
      String methodReference =
        LambdaCanBeMethodReferenceInspection.convertToMethodReference(body, parameters, lambda.getFunctionalInterfaceType(), lambda);
      if(methodReference != null) {
        lambda.replace(factory.createExpressionFromText(methodReference, lambda));
      } else {
        PsiParameter parameter = parameters[0];
        String name = parameter.getName();
        String nameCandidate = null;
        if(name != null) {
          if(name.length() > 1 && name.endsWith("1")) {
            nameCandidate = name.substring(0, name.length()-1);
          }
        }
        if(nameCandidate != null) {
          String newName = JavaCodeStyleManager.getInstance(expression.getProject()).suggestUniqueVariableName(nameCandidate, lambda, true);
          if(newName.equals(nameCandidate)) {
            Collection<PsiReferenceExpression> references = PsiTreeUtil.collectElementsOfType(body, PsiReferenceExpression.class);
            StreamEx.of(references).filter(ref -> ref.resolve() == parameter).map(PsiJavaCodeReferenceElement::getReferenceNameElement)
              .nonNull().forEach(nameElement -> nameElement.replace(factory.createIdentifier(newName)));
            parameter.setName(newName);
          }
        }
      }
    }
  }
}
