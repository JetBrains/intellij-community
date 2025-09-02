// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

public final class PsiPolyExpressionUtil {
  public static boolean hasStandaloneForm(PsiExpression expression) {
    return !(expression instanceof PsiFunctionalExpression) &&
           !(expression instanceof PsiParenthesizedExpression) &&
           !(expression instanceof PsiConditionalExpression) &&
           !(expression instanceof PsiSwitchExpression) &&
           !(expression instanceof PsiCallExpression);
  }

  public static boolean isPolyExpression(final PsiExpression expression) {
    if (expression instanceof PsiFunctionalExpression) {
      return true;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      return isPolyExpression(((PsiParenthesizedExpression)expression).getExpression());
    }
    if (expression instanceof PsiNewExpression && PsiDiamondType.hasDiamond((PsiNewExpression)expression)) {
      return isInAssignmentOrInvocationContext(expression);
    }
    if (expression instanceof PsiMethodCallExpression) {
      return isMethodCallPolyExpression(expression, expr -> ((PsiMethodCallExpression)expr).resolveMethod());
    }
    if (expression instanceof PsiConditionalExpression) {
      final ConditionalKind conditionalKind = isBooleanOrNumeric(expression);
      if (conditionalKind == null) {
        return isInAssignmentOrInvocationContext(expression);
      }
    }
    if (expression instanceof PsiSwitchExpression) {
      return isInAssignmentOrInvocationContext(expression);
    }
    return false;
  }

  public static boolean isMethodCallPolyExpression(PsiExpression expression, final PsiMethod method) {
    return isMethodCallPolyExpression(expression, e -> method);
  }

  private static boolean isMethodCallPolyExpression(PsiExpression expression, Function<? super PsiExpression, ? extends PsiMethod> methodResolver) {
    if (isInAssignmentOrInvocationContext(expression) && ((PsiCallExpression)expression).getTypeArguments().length == 0) {
      PsiMethod method = methodResolver.apply(expression);
      return method == null || isMethodCallTypeDependsOnInference(expression, method);
    }
    return false;
  }

  private static boolean isMethodCallTypeDependsOnInference(PsiExpression expression, PsiMethod method) {
    final Set<PsiTypeParameter> typeParameters = ContainerUtil.newHashSet(method.getTypeParameters());
    if (!typeParameters.isEmpty()) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null) {
        return PsiTypesUtil.mentionsTypeParameters(returnType, typeParameters);
      }
    }
    else if (method.isConstructor() && expression instanceof PsiNewExpression && PsiDiamondType.hasDiamond((PsiNewExpression)expression)) {
      return true;
    }
    return false;
  }

  public static boolean isInAssignmentOrInvocationContext(PsiExpression expr) {
    final PsiElement context = PsiUtil.skipParenthesizedExprUp(expr.getParent());
    return context instanceof PsiExpressionList ||
           context instanceof PsiArrayInitializerExpression ||
           context instanceof PsiConditionalExpression && (expr instanceof PsiCallExpression || isPolyExpression((PsiExpression)context)) ||
           isSwitchExpressionAssignmentOrInvocationContext(expr) ||
           isAssignmentContext(expr, context);
  }

  private static boolean isSwitchExpressionAssignmentOrInvocationContext(PsiExpression expr) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expr).getParent();
    if (parent instanceof PsiExpressionStatement && parent.getParent() instanceof PsiSwitchLabeledRuleStatement ||
        parent instanceof PsiBreakStatement ||
        parent instanceof PsiYieldStatement ||
        parent instanceof PsiThrowStatement) {
      PsiSwitchExpression switchExpression = PsiTreeUtil.getParentOfType(expr, PsiSwitchExpression.class, true, PsiMember.class, PsiLambdaExpression.class);
      return switchExpression  != null &&
             PsiUtil.getSwitchResultExpressions(switchExpression).contains(expr) &&
             isInAssignmentOrInvocationContext(switchExpression);
    }
    return false;
  }

  private static boolean isAssignmentContext(PsiExpression expr, PsiElement context) {
    return PsiUtil.isCondition(expr, context) ||
           context instanceof PsiReturnStatement ||
           context instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)context).getOperationTokenType() == JavaTokenType.EQ ||
           context instanceof PsiVariable && !isVarContext((PsiVariable)context) ||
           context instanceof PsiLambdaExpression;
  }

  private static boolean isVarContext(PsiVariable variable) {
    if (PsiUtil.isAvailable(JavaFeature.LVTI, variable)) {
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
      return type instanceof PsiPrimitiveType && type != PsiTypes.nullType();
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
    else if (arg instanceof PsiSwitchExpression) {
      return isBooleanOrNumeric(arg) != null;
    }
    else {
      assert false : arg;
      return false;
    }
  }

  private enum ConditionalKind {
    BOOLEAN, NUMERIC, NULL
  }
  
  public static boolean sameBooleanOrNumeric(PsiExpression thenExpression, PsiExpression elseExpression) {
    final ConditionalKind thenKind = isBooleanOrNumeric(thenExpression);
    final ConditionalKind elseKind = isBooleanOrNumeric(elseExpression);
    if (thenKind == elseKind || elseKind == ConditionalKind.NULL) return thenKind != null;
    if (thenKind == ConditionalKind.NULL) return elseKind != null;
    return false;
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
      final JavaResolveResult result = ((PsiMethodCallExpression)expr).getMethodExpression().advancedResolve(false);
      final PsiMethod method = (PsiMethod)result.getElement();
      if (method != null) {
        type = method.getReturnType();
        if (result instanceof MethodCandidateInfo) {
          // Spec: Note that, for a generic method, this is the type before instantiating the method's type arguments.
          PsiSubstitutor substitutor = ((MethodCandidateInfo)result).getSubstitutorFromQualifier();
          type = substitutor.substitute(type);
        }
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

    if (expr instanceof PsiSwitchExpression) {
      ConditionalKind switchKind = null;
      for (PsiExpression resultExpression : PsiUtil.getSwitchResultExpressions((PsiSwitchExpression)expr)) {
        ConditionalKind resultKind = isBooleanOrNumeric(resultExpression);
        if (resultKind == null) return null;
        if (switchKind == null) {
          switchKind = resultKind;
        }
        else if (switchKind != resultKind) {
          if (switchKind == ConditionalKind.NULL) {
            switchKind = resultKind;
          }
          else if (resultKind != ConditionalKind.NULL) {
            return null;
          }
        }
      }
      return switchKind;
    }
    return null;
  }

  private static @Nullable ConditionalKind isBooleanOrNumericType(PsiType type) {
    if (type == PsiTypes.nullType()) {
      return ConditionalKind.NULL;
    }

    if (TypeConversionUtil.isNumericType(type)) return ConditionalKind.NUMERIC;
    if (TypeConversionUtil.isBooleanType(type)) return ConditionalKind.BOOLEAN;
    return null;
  }
}
