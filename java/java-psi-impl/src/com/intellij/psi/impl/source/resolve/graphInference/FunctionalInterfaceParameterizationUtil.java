/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;

public class FunctionalInterfaceParameterizationUtil {
  private static final Logger LOG = Logger.getInstance("#" + FunctionalInterfaceParameterizationUtil.class.getName());

  public static boolean isWildcardParameterized(@Nullable PsiType classType) {
    if (classType == null) return false;
    if (classType instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)classType).getConjuncts()) {
        if (!isWildcardParameterized(type)) return false;
      }
    }
    if (classType instanceof PsiClassType) {
      for (PsiType type : ((PsiClassType)classType).getParameters()) {
        if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) {
          return true;
        }
      }
      return false;
    }
    return false;
  }

  @Nullable
  public static PsiType getFunctionalType(@Nullable PsiType psiClassType, PsiLambdaExpression expr) {
    return getFunctionalType(psiClassType, expr, true);
  }

  @Nullable
  public static PsiType getFunctionalType(@Nullable PsiType psiClassType, PsiLambdaExpression expr, boolean resolve) {
    if (!expr.hasFormalParameterTypes() || expr.getParameterList().getParametersCount() == 0) return psiClassType;
    if (!isWildcardParameterized(psiClassType)) {
      return psiClassType;
    }
    final PsiParameter[] lambdaParams = expr.getParameterList().getParameters();
    if (psiClassType instanceof PsiIntersectionType) {
      for (PsiType psiType : ((PsiIntersectionType)psiClassType).getConjuncts()) {
        final PsiType functionalType = getFunctionalType(psiType, expr, false);
        if (functionalType != null) return functionalType;
      }
      return null;
    }

    LOG.assertTrue(psiClassType instanceof PsiClassType, "Unexpected type: " + psiClassType);
    final PsiType[] parameters = ((PsiClassType)psiClassType).getParameters();
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)psiClassType).resolveGenerics();
    PsiClass psiClass = resolveResult.getElement();

    if (psiClass != null) {

      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod == null) return null;

      final InferenceSession session = new InferenceSession(PsiSubstitutor.EMPTY);
      PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
      if (typeParameters.length != parameters.length) {
        return null;
      }

      for (int i = 0; i < typeParameters.length; i++) {
        session.addVariable(typeParameters[i], parameters[i]);
      }

      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      final PsiParameter[] targetMethodParams = interfaceMethod.getParameterList().getParameters();
      if (targetMethodParams.length != lambdaParams.length) {
        return null;
      }
      for (int i = 0; i < targetMethodParams.length; i++) {
        if (resolve) {
          session.addConstraint(new TypeEqualityConstraint(lambdaParams[i].getType(), targetMethodParams[i].getType()));
        }
      }

      final PsiClassType parameterization = elementFactory.createType(psiClass, session.infer());
      if (!TypeConversionUtil.containsWildcards(parameterization)) return parameterization;
    }
    return null;
  }
}
