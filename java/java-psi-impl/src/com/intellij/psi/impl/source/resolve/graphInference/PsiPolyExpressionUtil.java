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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 */
public class PsiPolyExpressionUtil {
  public static boolean hasStandaloneForm(PsiExpression expression) {
    if (expression instanceof PsiLambdaExpression ||
        expression instanceof PsiMethodReferenceExpression ||
        expression instanceof PsiParenthesizedExpression ||
        expression instanceof PsiConditionalExpression ||
        expression instanceof PsiCallExpression) {
      return false;
    }
    return true;
  }

  public static boolean isPolyExpression(PsiExpression expression) {
    if (expression instanceof PsiLambdaExpression || expression instanceof PsiMethodReferenceExpression) {
      return true;
    } 
    else if (expression instanceof PsiParenthesizedExpression) {
      return isPolyExpression(((PsiParenthesizedExpression)expression).getExpression());
    }
    else if (expression instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)expression).getClassReference();
      if (classReference != null) {
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();
          if (typeElements.length == 1 && typeElements[0].getType() instanceof PsiDiamondType) {
            return isAssignmentOrInvocationContext(expression.getParent());
          }
        }
      }
    } else if (expression instanceof PsiMethodCallExpression) {
      if (isAssignmentOrInvocationContext(expression.getParent()) && ((PsiMethodCallExpression)expression).getTypeArguments().length == 0) {
        final PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
        if (method != null) {
          final Set<PsiTypeParameter> typeParameters = new HashSet<PsiTypeParameter>(Arrays.asList(method.getTypeParameters()));
          if (typeParameters.size() > 0) {
            final PsiType returnType = method.getReturnType();
            if (returnType != null) {
              return mentionsTypeParameters(returnType, typeParameters);
            }
          }
        }
      }
    }
    else if (expression instanceof PsiConditionalExpression) {
      final ConditionalKind conditionalKind = isBooleanOrNumeric(expression);
      if (conditionalKind == null) {
        return isAssignmentOrInvocationContext(expression.getParent());
      }
    }
    return false;
  }

  public static PsiType getTargetType(@NotNull PsiCallExpression expression) {
    return PsiTypesUtil.getExpectedTypeByParent(expression);
  }
  
  public static Boolean mentionsTypeParameters(PsiType returnType, final Set<PsiTypeParameter> typeParameters) {
    return returnType.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitType(PsiType type) {
        return false;
      }

      @Nullable
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        if (bound != null) {
          return bound.accept(this);
        }
        return false;
      }

      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        for (PsiType type : classType.getParameters()) {
          if (type.accept(this)) return true;
        }
        final PsiClass psiClass = classType.resolve();
        return psiClass instanceof PsiTypeParameter && typeParameters.contains(psiClass);
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }
    });
  }

  private static boolean isAssignmentOrInvocationContext(PsiElement context) {
    return context instanceof PsiExpressionList || isAssignmentContext(context);
  }

  private static boolean isAssignmentContext(PsiElement context) {
    return context instanceof PsiReturnStatement ||
           context instanceof PsiAssignmentExpression ||
           context instanceof PsiVariable;
  }

  private enum ConditionalKind {
    BOOLEAN, NUMERIC
  }

  private static ConditionalKind isBooleanOrNumeric(PsiExpression expr) {
    if (expr instanceof PsiParenthesizedExpression) {
      return isBooleanOrNumeric(((PsiParenthesizedExpression)expr).getExpression());
    }
    PsiType type = null;
    if (expr instanceof PsiNewExpression || hasStandaloneForm(expr)) {
      type = expr.getType();
    } else if (expr instanceof PsiMethodCallExpression) {
      final PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
      if (method != null) {
        type = method.getReturnType();
      }
    }
    if (TypeConversionUtil.isNumericType(type)) return ConditionalKind.NUMERIC;
    if (TypeConversionUtil.isBooleanType(type)) return ConditionalKind.BOOLEAN;
    if (expr instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)expr).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression)expr).getElseExpression();
      final ConditionalKind thenKind = isBooleanOrNumeric(thenExpression);
      final ConditionalKind elseKind = isBooleanOrNumeric(elseExpression);
      if (thenKind == elseKind) return thenKind;
    }
    return null;
  }
}
