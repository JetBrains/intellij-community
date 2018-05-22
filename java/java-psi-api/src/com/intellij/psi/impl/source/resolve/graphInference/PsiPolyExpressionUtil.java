// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

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
    else if (expression instanceof PsiNewExpression && PsiDiamondType.hasDiamond((PsiNewExpression)expression)) {
      return isInAssignmentOrInvocationContext(expression);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      return isMethodCallPolyExpression(expression, expr -> {
        final MethodCandidateInfo.CurrentCandidateProperties candidateProperties =
          MethodCandidateInfo.getCurrentMethod(((PsiMethodCallExpression)expr).getArgumentList());
        return candidateProperties != null ? candidateProperties.getMethod() : ((PsiMethodCallExpression)expr).resolveMethod();
      });
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
    return isMethodCallPolyExpression(expression, e -> method);
  }

  private static boolean isMethodCallPolyExpression(PsiExpression expression, Function<PsiExpression, PsiMethod> methodResolver) {
    if (isInAssignmentOrInvocationContext(expression) && ((PsiCallExpression)expression).getTypeArguments().length == 0) {
      PsiMethod method = methodResolver.apply(expression);
      return method == null || isMethodCallTypeDependsOnInference(expression, method);
    }
    return false;
  }

  public static boolean isMethodCallTypeDependsOnInference(PsiExpression expression, PsiMethod method) {
    final Set<PsiTypeParameter> typeParameters = new HashSet<>(Arrays.asList(method.getTypeParameters()));
    if (!typeParameters.isEmpty()) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null) {
        return mentionsTypeParameters(returnType, typeParameters);
      }
    }
    else if (method.isConstructor() && expression instanceof PsiNewExpression && PsiDiamondType.hasDiamond((PsiNewExpression)expression)) {
      return true;
    }
    return false;
  }

  public static Boolean mentionsTypeParameters(@Nullable PsiType returnType, final Set<PsiTypeParameter> typeParameters) {
    if (returnType == null) return false;
    return returnType.accept(new PsiTypeVisitor<Boolean>() {
      @NotNull
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

      @NotNull
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        PsiClassType.ClassResolveResult result = classType.resolveGenerics();
        final PsiClass psiClass = result.getElement();
        if (psiClass != null) {
          PsiSubstitutor substitutor = result.getSubstitutor();
          for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(psiClass)) {
            PsiType type = substitutor.substitute(parameter);
            if (type != null && type.accept(this)) return true;
          }
        }
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
           context instanceof PsiVariable && !isVarContext((PsiVariable)context) ||
           context instanceof PsiLambdaExpression;
  }

  private static boolean isVarContext(PsiVariable variable) {
    if (PsiUtil.isLanguageLevel10OrHigher(variable)) {
      PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && typeElement.isInferredType()) {
        return true;
      }
    }
    return false;
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
    BOOLEAN, NUMERIC, NULL
  }

  private static ConditionalKind isBooleanOrNumeric(PsiExpression expr) {
    if (expr instanceof PsiParenthesizedExpression) {
      return isBooleanOrNumeric(((PsiParenthesizedExpression)expr).getExpression());
    }
    if (expr == null) return null;
    PsiType type = null;
    //A class instance creation expression (p15.9) for a class that is convertible to a numeric type.
    //As numeric classes do not have type parameters, at this point expressions with diamonds could be ignored
    if (expr instanceof PsiNewExpression && !PsiDiamondType.hasDiamond((PsiNewExpression)expr) ||
        hasStandaloneForm(expr)) {
      type = expr.getType();
    }
    else if (expr instanceof PsiMethodCallExpression) {
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
      if (thenKind == elseKind || elseKind == ConditionalKind.NULL) return thenKind;
      if (thenKind == ConditionalKind.NULL) return elseKind;
    }
    return null;
  }

  @Nullable
  private static ConditionalKind isBooleanOrNumericType(PsiType type) {
    if (type == PsiType.NULL) {
      return ConditionalKind.NULL;
    }

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
