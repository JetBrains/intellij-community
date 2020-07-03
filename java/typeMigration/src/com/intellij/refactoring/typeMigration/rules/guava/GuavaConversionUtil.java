/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.inspections.GuavaConversionSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class GuavaConversionUtil {
  private final static Logger LOG = Logger.getInstance(GuavaConversionUtil.class);

  @Nullable
  public static PsiType getFunctionReturnType(PsiExpression functionExpression) {
    if (functionExpression instanceof PsiFunctionalExpression) {
      return LambdaUtil.getFunctionalInterfaceReturnType((PsiFunctionalExpression)functionExpression);
    }
    PsiType currentType = functionExpression.getType();
    if (currentType == null) return null;

    while (true) {
      if (LambdaUtil.isFunctionalType(currentType)) {
        return LambdaUtil.getFunctionalInterfaceReturnType(currentType);
      }
      final PsiType[] superTypes = currentType.getSuperTypes();
      currentType = null;
      for (PsiType type : superTypes) {
        final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
        if (aClass != null && InheritanceUtil.isInheritor(aClass, GuavaLambda.FUNCTION.getClassQName())) {
          currentType = type;
          break;
        }
      }
      if (currentType == null) {
        return null;
      }
    }
  }

  @NotNull
  public static PsiType addTypeParameters(@NotNull String baseClassQualifiedName, @Nullable PsiType type, @NotNull PsiElement context) {
    String parameterText = "";
    if (type != null) {
      final String canonicalText = type.getCanonicalText(false);
      if (canonicalText.contains("<")) {
        parameterText = canonicalText.substring(canonicalText.indexOf('<'));
      }
    }
    return JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(baseClassQualifiedName + parameterText, context);
  }

  public static boolean isJavaLambda(PsiElement element, TypeEvaluator evaluator) {
    if (element instanceof PsiLocalVariable) {
      return GuavaLambda.findJavaAnalogueFor(evaluator.getType(element)) != null;
    }
    else if (element instanceof PsiReturnStatement) {
      final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class);
      PsiType methodReturnType = null;
      if (methodOrLambda instanceof PsiMethod) {
        methodReturnType = evaluator.getType(methodOrLambda);
      }
      return GuavaLambda.findJavaAnalogueFor(methodReturnType) != null;
    }
    else if (element instanceof PsiExpressionList) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return evaluator.getType(parent) != null;
      }
    }
    return false;
  }

  public static PsiExpression adjustLambdaContainingExpression(PsiExpression expression,
                                                               boolean insertTypeCase,
                                                               PsiType targetType,
                                                               @NotNull TypeEvaluator evaluator) {
    if (expression instanceof PsiNewExpression) {
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)expression).getAnonymousClass();
      if (anonymousClass != null) {
        return convertAnonymousClass((PsiNewExpression)expression, anonymousClass, evaluator);
      }
      else {
        final GuavaLambda lambda = GuavaLambda.findFor(evaluator.evaluateType(expression));
        if (lambda == null) {
          return expression;
        }
        else {
          final PsiExpression expressionWithMethodReference = addMethodReference(expression, lambda);
          if (insertTypeCase) {
            return adjustLambdaContainingExpression(expressionWithMethodReference, true, targetType, evaluator);
          } else {
            return expressionWithMethodReference;
          }
        }
      }
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      final PsiExpression qualifier = ((PsiMethodReferenceExpression)expression).getQualifierExpression();
      final PsiType evaluatedType = evaluator.evaluateType(qualifier);
      final GuavaLambda lambda = GuavaLambda.findJavaAnalogueFor(evaluatedType);
      if (lambda != null) {
        PsiExpression replaced = (PsiExpression)expression.replace(qualifier);
        if (targetType == null &&
            lambda.getSamName().equals(((PsiMethodReferenceExpression)expression).getReferenceName())) {
          return replaced;
        }
        return adjustLambdaContainingExpression(replaced, insertTypeCase, targetType, evaluator);
      }
    }
    if (expression instanceof PsiFunctionalExpression) {
      if (insertTypeCase) {
        return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText("((" + targetType.getCanonicalText() + ")" + expression.getText() + ")", expression);
      }
    }
    else if (expression instanceof PsiMethodCallExpression || expression instanceof PsiReferenceExpression) {
      final GuavaLambda lambda = GuavaLambda.findFor(evaluator.evaluateType(expression));
      if (lambda != null) {
        expression = addMethodReference(expression, lambda);
        return adjustLambdaContainingExpression(expression, insertTypeCase, targetType, evaluator);
      }
    }
    return expression;
  }

  public static PsiExpression convertAnonymousClass(@NotNull PsiNewExpression expression,
                                                    @NotNull PsiAnonymousClass anonymousClass,
                                                    @NotNull TypeEvaluator typeEvaluator) {
    final GuavaConversionSettings settings = typeEvaluator.getSettings(GuavaConversionSettings.class);
    final Set<String> ignoredAnnotations = settings != null ? settings.getIgnoredAnnotations() : Collections.emptySet();
    if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(anonymousClass, true, ignoredAnnotations)) {
      return AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(expression, true, true);
    } else {
      return tryConvertClassAndSamNameToJava(expression);
    }
  }

  public static PsiExpression tryConvertClassAndSamNameToJava(PsiNewExpression expression) {
    final GuavaLambda lambda = GuavaLambda.findFor(expression.getType());
    if (lambda == null) return expression;
    final PsiAnonymousClass aClass = expression.getAnonymousClass();
    LOG.assertTrue(aClass != null);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());

    if (!lambda.getSamName().equals(lambda.getJavaAnalogueSamName())) {
      boolean isFound = false;
      for (PsiMethod method : aClass.findMethodsByName(lambda.getSamName(), false)) {
        if (method.getParameterList().getParametersCount() == lambda.getParametersCount()) {
          for (PsiMethod psiMethod : method.findSuperMethods()) {
            final PsiClass superMethodContainingClass = psiMethod.getContainingClass();
            if (superMethodContainingClass != null && lambda.getClassQName().equals(superMethodContainingClass.getQualifiedName())) {
              final PsiIdentifier methodNameIdentifier = method.getNameIdentifier();
              LOG.assertTrue(methodNameIdentifier != null);
              methodNameIdentifier.replace(factory.createIdentifier(lambda.getJavaAnalogueSamName()));
              isFound = true;
              break;
            }
          }
        }
        if (isFound) break;
      }
    }

    final PsiElement currentClassName = aClass.getBaseClassReference().getReferenceNameElement();
    if (currentClassName != null) {
      final PsiElement newNameElement = factory.createReferenceFromText(lambda.getJavaAnalogueClassQName(), null);
      currentClassName.replace(newNameElement);
    }

    return (PsiExpression)expression.replace(factory.createExpressionFromText(expression.getText(), null));
  }

  private static PsiExpression addMethodReference(@NotNull PsiExpression expression, @NotNull GuavaLambda lambda) {
    return (PsiExpression)expression.replace(JavaPsiFacade.getElementFactory(expression.getProject())
                                                     .createExpressionFromText(expression.getText() + "::" + lambda.getSamName(), expression));
  }
}
