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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ComparatorCombinatorsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + ComparatorCombinatorsInspection.class.getName());

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @SuppressWarnings("DialogTitleCapitalization")
      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiType type = lambda.getFunctionalInterfaceType();
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 2 ||
            !(type instanceof PsiClassType) ||
            !((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          return;
        }
        PsiElement body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
        String methodName = null;
        if (body instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCall = (PsiMethodCallExpression)body;
          PsiExpression[] args = methodCall.getArgumentList().getExpressions();
          if (args.length == 1 && MethodUtils.isCompareToCall(methodCall)) {
            PsiExpression left = methodCall.getMethodExpression().getQualifierExpression();
            PsiExpression right = args[0];
            if (left instanceof PsiReferenceExpression && right instanceof PsiReferenceExpression) {
              PsiElement leftElement = ((PsiReferenceExpression)left).resolve();
              PsiElement rightElement = ((PsiReferenceExpression)right).resolve();
              if (leftElement == parameters[0] && rightElement == parameters[1]) {
                methodName = "naturalOrder";
              }
              else if (leftElement == parameters[1] && rightElement == parameters[0]) {
                methodName = "reverseOrder";
              }
            }
            if (methodName == null && areEquivalent(parameters, left, right)) {
              methodName = "comparing";
            }
          }
          else {
            PsiMethod method = methodCall.resolveMethod();
            if (args.length == 2 && method != null && method.getName().equals("compare")) {
              PsiClass compareClass = method.getContainingClass();
              if (compareClass != null) {
                if (CommonClassNames.JAVA_LANG_DOUBLE.equals(compareClass.getQualifiedName())) {
                  methodName = "comparingDouble";
                }
                else if (CommonClassNames.JAVA_LANG_INTEGER.equals(compareClass.getQualifiedName())) {
                  methodName = "comparingInt";
                }
                else if (CommonClassNames.JAVA_LANG_LONG.equals(compareClass.getQualifiedName())) {
                  methodName = "comparingLong";
                }
                else {
                  return;
                }
                if (!areEquivalent(parameters, args[0], args[1])) return;
              }
            }
          }
        } else if (body instanceof PsiBinaryExpression) {
          PsiBinaryExpression binOp = (PsiBinaryExpression)body;
          if (binOp.getOperationTokenType().equals(JavaTokenType.MINUS) &&
              areEquivalent(parameters, binOp.getLOperand(), binOp.getROperand())) {
            methodName = "comparingInt";
          }
        }
        if (methodName != null) {
          holder
            .registerProblem(lambda, "Can be replaced with Comparator." + methodName,
                             ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithComparatorFix(methodName));
        }
      }
    };
  }

  private static boolean areEquivalent(PsiParameter[] parameters, PsiExpression left, PsiExpression right) {
    if (!PsiTreeUtil.processElements(left, e -> !(e instanceof PsiReferenceExpression) ||
                                                ((PsiReferenceExpression)e).resolve() != parameters[1]) ||
        !PsiTreeUtil.processElements(right, e -> !(e instanceof PsiReferenceExpression) ||
                                                 ((PsiReferenceExpression)e).resolve() != parameters[0])) {
      return false;
    }
    PsiExpression copy = (PsiExpression)right.copy();
    PsiElement[] rightRefs = PsiTreeUtil.collectElements(copy, e -> e instanceof PsiReferenceExpression &&
                                                                    ((PsiReferenceExpression)e).resolve() == parameters[1]);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(left.getProject());
    String paramName = parameters[0].getName();
    if (paramName == null) return false;
    for (PsiElement ref : rightRefs) {
      PsiElement nameElement = ((PsiReferenceExpression)ref).getReferenceNameElement();
      LOG.assertTrue(nameElement != null);
      nameElement.replace(factory.createIdentifier(paramName));
    }
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, copy);
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
      return "Replace with Comparator." + myMethodName;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify comparator using Comparator static methods";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiLambdaExpression)) return;
      PsiLambdaExpression lambda = (PsiLambdaExpression)element;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 2) return;
      PsiElement body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      PsiExpression keyExtractor = null;
      String methodName = null;
      if (body instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)body;
        if (MethodUtils.isCompareToCall(methodCall)) {
          methodName = "comparing";
          keyExtractor = methodCall.getMethodExpression().getQualifierExpression();
          if (keyExtractor instanceof PsiReferenceExpression) {
            PsiElement keyElement = ((PsiReferenceExpression)keyExtractor).resolve();
            if (keyElement == parameters[0]) {
              methodName = "naturalOrder";
            }
            else if (keyElement == parameters[1]) {
              methodName = "reverseOrder";
            }
          }
        }
        else {
          PsiMethod method = methodCall.resolveMethod();
          if (method != null && method.getName().equals("compare")) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              String className = containingClass.getQualifiedName();
              if (className != null) {
                PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                if (args.length != 2) return;
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
      } else if(body instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)body;
        if(!binOp.getOperationTokenType().equals(JavaTokenType.MINUS)) return;
        methodName = "comparingInt";
        keyExtractor = binOp.getLOperand();
      }
      if (methodName == null || keyExtractor == null) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      String parameterName = parameters[0].getName();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiElement result;
      if (!methodName.startsWith("comparing")) {
        result = lambda.replace(factory.createExpressionFromText("java.util.Comparator." + methodName + "()", element));
      }
      else {
        String newLambda = parameterName + " -> " + keyExtractor.getText();
        PsiExpression replacement = factory.createExpressionFromText("java.util.Comparator." + methodName + "(" + newLambda + ")", element);
        result = lambda.replace(replacement);
        normalizeLambda(((PsiMethodCallExpression)result).getArgumentList().getExpressions()[0], factory);
      }
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }

    private static void normalizeLambda(PsiExpression expression, PsiElementFactory factory) {
      if (!(expression instanceof PsiLambdaExpression)) return;
      PsiLambdaExpression lambda = (PsiLambdaExpression)expression;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      PsiElement body = lambda.getBody();
      if (body == null) return;
      if (LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda) == lambda) {
        PsiParameter parameter = parameters[0];
        String name = parameter.getName();
        SuggestedNameInfo nameCandidate = null;
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
        if (name != null) {
          if (name.length() > 1 && name.endsWith("1")) {
            nameCandidate = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, name.substring(0, name.length() - 1),
                                                                 null, parameter.getType(), true);
          } else if (name.equals("first")) {
            nameCandidate =
              codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, parameter.getType(), true);
          }
        }
        if (nameCandidate != null) {
          String[] names = codeStyleManager.suggestUniqueVariableName(nameCandidate, lambda, true).names;
          if (names.length > 0) {
            String newName = names[0];
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
