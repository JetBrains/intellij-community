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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 */
public class LambdaHighlightingUtil {
  private static final Logger LOG = Logger.getInstance("#" + LambdaHighlightingUtil.class.getName());

  @Nullable
  public static String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    return checkInterfaceFunctional(psiClass, "Target type of a lambda conversion must be an interface");
  }

  @Nullable
  static String checkInterfaceFunctional(@NotNull PsiClass psiClass, String interfaceNonFunctionalMessage) {
    if (psiClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
    final List<HierarchicalMethodSignature> signatures = LambdaUtil.findFunctionCandidates(psiClass);
    if (signatures == null) return interfaceNonFunctionalMessage;
    if (signatures.isEmpty()) return "No target method found";
    if (signatures.size() == 1) {
      return null;
    }
    return "Multiple non-overriding abstract methods found in interface " + HighlightUtil.formatClass(psiClass);
  }

  @Nullable
  static HighlightInfo checkParametersCompatible(PsiLambdaExpression expression,
                                                 PsiParameter[] methodParameters,
                                                 PsiSubstitutor substitutor) {
    final PsiParameter[] lambdaParameters = expression.getParameterList().getParameters();
    String incompatibleTypesMessage = "Incompatible parameter types in lambda expression: ";
    if (lambdaParameters.length != methodParameters.length) {
      incompatibleTypesMessage += "wrong number of parameters: expected " + methodParameters.length + " but found " + lambdaParameters.length;
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(expression.getParameterList())
        .descriptionAndTooltip(incompatibleTypesMessage)
        .create();
    }
    boolean hasFormalParameterTypes = expression.hasFormalParameterTypes();
    for (int i = 0; i < lambdaParameters.length; i++) {
      PsiParameter lambdaParameter = lambdaParameters[i];
      PsiType lambdaParameterType = lambdaParameter.getType();
      PsiType substitutedParamType = substitutor.substitute(methodParameters[i].getType());
      if (hasFormalParameterTypes &&!PsiTypesUtil.compareTypes(lambdaParameterType, substitutedParamType, true) ||
          !TypeConversionUtil.isAssignable(substitutedParamType, lambdaParameterType)) {
        final String expectedType = substitutedParamType != null ? substitutedParamType.getPresentableText() : null;
        final String actualType = lambdaParameterType.getPresentableText();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(expression.getParameterList())
          .descriptionAndTooltip(incompatibleTypesMessage + "expected " + expectedType + " but found " + actualType)
          .create();
      }
    }
    return null;
  }

  public static boolean insertSemicolonAfter(PsiLambdaExpression lambdaExpression) {
    return lambdaExpression.getBody() instanceof PsiCodeBlock || !insertSemicolon(lambdaExpression.getParent());
  }

  public static boolean insertSemicolon(PsiElement parent) {
    return parent instanceof PsiExpressionList || parent instanceof PsiExpression;
  }

  @Nullable
  public static String checkInterfaceFunctional(PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType) {
      final Set<MethodSignature> signatures = new HashSet<>();
      for (PsiType type : ((PsiIntersectionType)functionalInterfaceType).getConjuncts()) {
        if (checkInterfaceFunctional(type) == null) {
          final MethodSignature signature = LambdaUtil.getFunction(PsiUtil.resolveClassInType(type));
          LOG.assertTrue(signature != null, type.getCanonicalText());
          signatures.add(signature);
        }
      }

      if (signatures.size() > 1) {
        return "Multiple non-overriding abstract methods found in " + functionalInterfaceType.getPresentableText();
      }
      return null;
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
      final List<HierarchicalMethodSignature> signatures = LambdaUtil.findFunctionCandidates(aClass);
      if (signatures != null && signatures.size() == 1) {
        final MethodSignature functionalMethod = signatures.get(0);
        if (functionalMethod.getTypeParameters().length > 0) return "Target method is generic";
      }
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
      final PsiType substitution = resolveResult.getSubstitutor().substitute(parameter);
      if (substitution instanceof PsiWildcardType && !((PsiWildcardType)substitution).isBounded()) {
        boolean depends = false;
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
