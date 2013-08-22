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
package com.intellij.psi;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 */
public class LambdaHighlightingUtil {
  @Nullable
  public static String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    return checkInterfaceFunctional(psiClass, "Target type of a lambda conversion must be an interface");
  }

  @Nullable
  public static String checkInterfaceFunctional(@NotNull PsiClass psiClass, String interfaceNonFunctionalMessage) {
    if (psiClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
    final List<MethodSignature> signatures = LambdaUtil.findFunctionCandidates(psiClass);
    if (signatures == null) return interfaceNonFunctionalMessage;
    if (signatures.isEmpty()) return "No target method found";
    if (signatures.size() == 1) {
      final MethodSignature functionalMethod = signatures.get(0);
      if (functionalMethod.getTypeParameters().length > 0) return "Target method is generic";
      return null;
    }
    return "Multiple non-overriding abstract methods found";
  }

  public static String checkReturnTypeCompatible(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceReturnType) {
    if (functionalInterfaceReturnType == PsiType.VOID) {
      final PsiElement body = lambdaExpression.getBody();
      if (body instanceof PsiCodeBlock) {
        if (!LambdaUtil.getReturnExpressions(lambdaExpression).isEmpty()) return "Unexpected return value";
      } else if (body instanceof PsiReferenceExpression || body instanceof PsiLiteralExpression) {
        final PsiType type = ((PsiExpression)body).getType();
        if (type != PsiType.VOID) {
          return "Incompatible return type " + (type == PsiType.NULL || type == null ? "<null>" : type.getPresentableText()) +" in lambda expression";
        }
      }
    } else if (functionalInterfaceReturnType != null) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      for (final PsiExpression expression : returnExpressions) {
        final PsiType expressionType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return expression.getType();
          }
        });
        if (expressionType != null && !functionalInterfaceReturnType.isAssignableFrom(expressionType)) {
          return "Incompatible return type " + expressionType.getPresentableText() + " in lambda expression";
        }
      }
      if (LambdaUtil.getReturnStatements(lambdaExpression).size() > returnExpressions.size() || returnExpressions.isEmpty() && !lambdaExpression.isVoidCompatible()) {
        return "Missing return value";
      }
    }
    return null;
  }

  public static boolean insertSemicolonAfter(PsiLambdaExpression lambdaExpression) {
     if (lambdaExpression.getBody() instanceof PsiCodeBlock) {
       return true;
     }
    if (insertSemicolon(lambdaExpression.getParent())) {
      return false;
    }
    return true;
  }

  public static boolean insertSemicolon(PsiElement parent) {
    return parent instanceof PsiExpressionList || parent instanceof PsiExpression;
  }

  @Nullable
  public static String checkInterfaceFunctional(PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)functionalInterfaceType).getConjuncts()) {
        if (checkInterfaceFunctional(type) == null) return null;
      }
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(GenericsUtil.eliminateWildcards(functionalInterfaceType));
    final PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (checkReturnTypeApplicable(resolveResult, aClass)) {
        return "No instance of type " + functionalInterfaceType.getPresentableText() + " exists so that lambda expression can be type-checked";
      }
      return checkInterfaceFunctional(aClass);
    }
    return functionalInterfaceType.getPresentableText() + " is not a functional interface";
  }

  private static boolean checkReturnTypeApplicable(PsiClassType.ClassResolveResult resolveResult, final PsiClass aClass) {
    final MethodSignature methodSignature = LambdaUtil.getFunction(aClass);
    if (methodSignature == null) return false;

    for (PsiTypeParameter parameter : aClass.getTypeParameters()) {
      if (parameter.getExtendsListTypes().length == 0) continue;
      boolean depends = false;
      final PsiType substitution = resolveResult.getSubstitutor().substitute(parameter);
      if (substitution instanceof PsiWildcardType && !((PsiWildcardType)substitution).isBounded()) {
        for (PsiType paramType : methodSignature.getParameterTypes()) {
          if (LambdaUtil.depends(paramType, new LambdaUtil.TypeParamsChecker((PsiMethod)null, aClass) {
            @Override
            public boolean startedInference() {
              return true;
            }
          }, parameter)) {
            depends = true;
            break;
          }
        }
        if (!depends) return true;
      }
    }
    return false;
  }
}
