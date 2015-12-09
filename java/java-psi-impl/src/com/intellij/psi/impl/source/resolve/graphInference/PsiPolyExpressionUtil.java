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
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 */
public class PsiPolyExpressionUtil {
  public static boolean hasStandaloneForm(PsiExpression expression) {
    if (expression instanceof PsiFunctionalExpression ||
        expression instanceof PsiParenthesizedExpression ||
        expression instanceof PsiConditionalExpression ||
        expression instanceof PsiCallExpression) {
      return false;
    }
    return true;
  }

  public static boolean isPolyExpression(final PsiExpression expression) {
    if (expression instanceof PsiFunctionalExpression) {
      return true;
    } 
    else if (expression instanceof PsiParenthesizedExpression) {
      return isPolyExpression(((PsiParenthesizedExpression)expression).getExpression());
    }
    else if (expression instanceof PsiNewExpression && PsiDiamondTypeUtil.hasDiamond((PsiNewExpression)expression)) {
      return isInAssignmentOrInvocationContext(expression);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final MethodCandidateInfo.CurrentCandidateProperties candidateProperties = MethodCandidateInfo.getCurrentMethod(((PsiMethodCallExpression)expression).getArgumentList());
      return isMethodCallPolyExpression(expression, candidateProperties != null ? candidateProperties.getMethod() : ((PsiMethodCallExpression)expression).resolveMethod());
    }
    else if (expression instanceof PsiConditionalExpression) {
      final ConditionalKind conditionalKind = isBooleanOrNumeric(expression);
      if (conditionalKind == null) {
        return isInAssignmentOrInvocationContext(expression);
      }
    }
    return false;
  }

   public static boolean isMethodCallPolyExpression(PsiExpression expression, final PsiMethod method) {
    if (isInAssignmentOrInvocationContext(expression) && ((PsiCallExpression)expression).getTypeArguments().length == 0) {
      if (method != null) {
        final Set<PsiTypeParameter> typeParameters = new HashSet<PsiTypeParameter>(Arrays.asList(method.getTypeParameters()));
        if (!typeParameters.isEmpty()) {
          final PsiType returnType = method.getReturnType();
          if (returnType != null) {
            return mentionsTypeParameters(returnType, typeParameters);
          }
        }
        else if (method.isConstructor() && expression instanceof PsiNewExpression && PsiDiamondTypeUtil.hasDiamond((PsiNewExpression)expression)) {
          return true;
        }
      } else {
        return true;
      }
    }
    return false;
  }

  public static Boolean mentionsTypeParameters(@Nullable PsiType returnType, final Set<PsiTypeParameter> typeParameters) {
    if (returnType == null) return false;
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

  private static boolean isInAssignmentOrInvocationContext(PsiExpression expr) {
    final PsiElement context = PsiUtil.skipParenthesizedExprUp(expr.getParent());
    return context instanceof PsiExpressionList || 
           context instanceof PsiArrayInitializerExpression || 
           context instanceof PsiConditionalExpression && (expr instanceof PsiCallExpression || isPolyExpression((PsiExpression)context)) || 
           isAssignmentContext(expr, context);
  }

  private static boolean isAssignmentContext(PsiExpression expr, PsiElement context) {
    return PsiUtil.isCondition(expr, context) ||
           context instanceof PsiReturnStatement ||
           context instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)context).getOperationTokenType() == JavaTokenType.EQ ||
           context instanceof PsiVariable ||
           context instanceof PsiLambdaExpression;
  }

  public static boolean isExpressionOfPrimitiveType(@Nullable PsiExpression arg) {
    if (arg != null && !isPolyExpression(arg)) {
      final PsiType type = arg.getType();
      return type instanceof PsiPrimitiveType && type != PsiType.NULL;
    }
    else if (arg instanceof PsiNewExpression || arg instanceof PsiFunctionalExpression) {
      return false;
    }
    else if (arg instanceof PsiParenthesizedExpression) {
      return isExpressionOfPrimitiveType(((PsiParenthesizedExpression)arg).getExpression());
    }
    else if (arg instanceof PsiConditionalExpression) {
      return isBooleanOrNumeric(arg) != null;
    }
    else if (arg instanceof PsiMethodCallExpression) {
      final PsiMethod method = ((PsiMethodCallExpression)arg).resolveMethod();
      return method != null && method.getReturnType() instanceof PsiPrimitiveType;
    }
    else {
      assert false : arg;
      return false;
    }
  }

  private enum ConditionalKind {
    BOOLEAN, NUMERIC
  }

  private static ConditionalKind isBooleanOrNumeric(PsiExpression expr) {
    if (expr instanceof PsiParenthesizedExpression) {
      return isBooleanOrNumeric(((PsiParenthesizedExpression)expr).getExpression());
    }
    if (expr == null) return null;
    PsiType type = null;
    if (expr instanceof PsiNewExpression || hasStandaloneForm(expr)) {
      type = expr.getType();
    } else if (expr instanceof PsiMethodCallExpression) {
      final PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
      if (method != null) {
        type = method.getReturnType();
      }
    }

    final ConditionalKind kind = isBooleanOrNumericType(type);
    if (kind != null) {
      return kind;
    }

    if (expr instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)expr).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression)expr).getElseExpression();
      final ConditionalKind thenKind = isBooleanOrNumeric(thenExpression);
      final ConditionalKind elseKind = isBooleanOrNumeric(elseExpression);
      if (thenKind == elseKind || elseKind == null) return thenKind;
      if (thenKind == null) return elseKind;
    }
    return null;
  }

  @Nullable
  private static ConditionalKind isBooleanOrNumericType(PsiType type) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (TypeConversionUtil.isNumericType(type)) return ConditionalKind.NUMERIC;
    if (TypeConversionUtil.isBooleanType(type)) return ConditionalKind.BOOLEAN;

    if (psiClass instanceof PsiTypeParameter) {
      for (PsiClassType classType : psiClass.getExtendsListTypes()) {
        final ConditionalKind kind = isBooleanOrNumericType(classType);
        if (kind != null) {
          return kind;
        }
      }
    }
    return null;
  }
}
