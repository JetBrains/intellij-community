// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class GuavaTypeConversionDescriptor extends TypeConversionDescriptor {
  private static final Logger LOG = Logger.getInstance(GuavaTypeConversionDescriptor.class);
  private final String myReplaceByStringSource;
  private final boolean myIterable;
  private boolean myConvertParameterAsLambda = true;

  GuavaTypeConversionDescriptor(@NonNls String stringToReplace,
                                @NonNls String replaceByString,
                                @NotNull PsiExpression expression) {
    super(stringToReplace, replaceByString);
    myReplaceByStringSource = replaceByString;
    myIterable = isIterable(expression);
  }

  public GuavaTypeConversionDescriptor setConvertParameterAsLambda(boolean convertParameterAsLambda) {
    myConvertParameterAsLambda = convertParameterAsLambda;
    return this;
  }

  @Override
  public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
    setReplaceByString(myReplaceByStringSource + (myIterable ? ".collect(java.util.stream.Collectors.toList())" : ""));
    if (myConvertParameterAsLambda) {
      LOG.assertTrue(expression instanceof PsiMethodCallExpression);
      final PsiExpression[] arguments = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions();
      if (arguments.length == 1) {
        GuavaConversionUtil.adjustLambdaContainingExpression(arguments[0], false, null, evaluator);
      }
    }
    return super.replace(expression, evaluator);
  }

  public static boolean isIterable(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiLocalVariable) {
      return isIterable(((PsiLocalVariable)parent).getType());
    }
    else if (parent instanceof PsiReturnStatement) {
      return isIterable(PsiTypesUtil.getMethodReturnType(parent));
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final PsiElement maybeMethodCallExpr = expressionList.getParent();
      if (maybeMethodCallExpr instanceof PsiMethodCallExpression) {
        final PsiMethod method = ((PsiMethodCallExpression)maybeMethodCallExpr).resolveMethod();
        if (method != null) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          final PsiExpression[] arguments = expressionList.getExpressions();
          if (parameters.length == arguments.length) {
            final int index = ArrayUtil.indexOf(arguments, expression);
            if (index != -1) {
              return isIterable(parameters[index].getType());
            }
          }
        }
      }
    }
    else if (parent instanceof PsiMethodCallExpression) {
      return isIterable((PsiExpression)parent);
    }
    return false;
  }

  private static boolean isIterable(@Nullable PsiType type) {
    PsiClass aClass = PsiTypesUtil.getPsiClass(type);
    return aClass != null && CommonClassNames.JAVA_LANG_ITERABLE.equals(aClass.getQualifiedName());
  }
}
