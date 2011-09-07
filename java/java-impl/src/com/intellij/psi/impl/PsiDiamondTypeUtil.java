/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 */
public class PsiDiamondTypeUtil {
  private static final Logger LOG = Logger.getInstance("#" + PsiDiamondTypeUtil.class.getName());

  private PsiDiamondTypeUtil() {
  }

  public static boolean canCollapseToDiamond(final PsiNewExpression expression,
                                             final PsiNewExpression context,
                                             final @Nullable PsiType expectedType) {
    if (PsiUtil.getLanguageLevel(context).isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      if (classReference != null) {
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();
          if (typeElements.length > 0) {
            if (typeElements.length == 1 && typeElements[0].getType() instanceof PsiDiamondType) return false;
            final PsiDiamondType.DiamondInferenceResult inferenceResult = PsiDiamondType.resolveInferredTypes(expression, context);
            if (inferenceResult.getErrorMessage() == null) {
              final List<PsiType> types = inferenceResult.getInferredTypes();
              PsiType[] typeArguments = null;
              if (expectedType instanceof PsiClassType) {
                typeArguments = ((PsiClassType)expectedType).getParameters();
              }
              if (typeArguments == null) {
                typeArguments = parameterList.getTypeArguments();
              }
              if (types.size() == typeArguments.length) {
                for (int i = 0, typeArgumentsLength = typeArguments.length; i < typeArgumentsLength; i++) {
                  PsiType typeArgument = typeArguments[i];
                  if (types.get(i) instanceof PsiWildcardType) {
                    final PsiWildcardType wildcardType = (PsiWildcardType)types.get(i);
                    final PsiType bound = wildcardType.getBound();
                    if (bound != null) {
                      if (wildcardType.isExtends()) {
                        if (bound.isAssignableFrom(typeArgument)) continue;
                      }
                      else {
                        if (typeArgument.isAssignableFrom(bound)) continue;
                      }
                    }
                  }
                  if (!typeArgument.equals(types.get(i))) {
                    return false;
                  }
                }
              }
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static PsiElement replaceExplicitWithDiamond(PsiElement psiElement) {
    if (psiElement instanceof PsiReferenceParameterList) {
      if (!CodeInsightUtilBase.prepareFileForWrite(psiElement.getContainingFile())) return psiElement;
      final PsiNewExpression expression =
        (PsiNewExpression)JavaPsiFacade.getElementFactory(psiElement.getProject()).createExpressionFromText("new a<>()", psiElement);
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      LOG.assertTrue(classReference != null);
      final PsiReferenceParameterList parameterList = classReference.getParameterList();
      LOG.assertTrue(parameterList != null);
      return psiElement.replace(parameterList);
    }
    return psiElement;
  }
}
