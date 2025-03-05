// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class AddTypeArgumentsFix extends MethodArgumentFix {
  private static final Logger LOG = Logger.getInstance(AddTypeArgumentsFix.class);

  private AddTypeArgumentsFix(@NotNull PsiExpressionList list, int i, @NotNull PsiType toType, @NotNull ArgumentFixerActionFactory factory) {
    super(list, i, toType, factory);
  }

  @Override
  public @NotNull String getText(@NotNull PsiExpressionList list) {
    if (list.getExpressionCount() == 1) {
      return QuickFixBundle.message("add.type.arguments.single.argument.text");
    }

    return QuickFixBundle.message("add.type.arguments.text", myIndex + 1);
  }

  private static class MyFixerActionFactory extends ArgumentFixerActionFactory {
    @Override
    public IntentionAction createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new AddTypeArgumentsFix(list, i, toType, this).asIntention();
    }

    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      return addTypeArguments(expression, toType);
    }

    @Override
    public boolean areTypesConvertible(final @NotNull PsiType exprType, final @NotNull PsiType parameterType, final @NotNull PsiElement context) {
      return !(exprType instanceof PsiPrimitiveType) && !(parameterType instanceof PsiPrimitiveType) || TypeConversionUtil.boxingConversionApplicable(exprType, parameterType);
    }
  }

  public static @Nullable PsiExpression addTypeArguments(@NotNull PsiExpression expression, @Nullable PsiType toType) {
    return addTypeArguments(expression, toType, true);
  }

  public static @Nullable PsiExpression addTypeArguments(@NotNull PsiExpression expression, @Nullable PsiType toType, boolean withShortening) {
    if (!PsiUtil.isAvailable(JavaFeature.GENERICS, expression)) return null;

    PsiExpression orig = expression;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression methodCall) {
      final PsiReferenceParameterList list = methodCall.getMethodExpression().getParameterList();
      if (list == null || list.getTypeArguments().length > 0) return null;
      final JavaResolveResult resolveResult = methodCall.resolveMethodGenerics();
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod method) {
        final PsiType returnType = method.getReturnType();
        if (returnType == null) return null;

        final PsiTypeParameter[] typeParameters = method.getTypeParameters();
        if (typeParameters.length > 0) {
          PsiType[] mappings = PsiType.createArray(typeParameters.length);
          PsiResolveHelper helper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
          LanguageLevel level = PsiUtil.getLanguageLevel(expression);
          for (int i = 0; i < typeParameters.length; i++) {
            PsiTypeParameter typeParameter = typeParameters[i];
            final PsiType substitution;
            if (toType == null) {
              substitution = resolveResult.getSubstitutor().substitute(typeParameter);
              if (!PsiTypesUtil.isDenotableType(substitution, expression)) return null;
            }
            else {
              substitution = helper.getSubstitutionForTypeParameter(typeParameter, returnType, toType, false, level);
            }
            if (substitution == null || PsiTypes.nullType().equals(substitution)) return null;
            mappings[i] = GenericsUtil.eliminateWildcards(substitution, false);
          }

          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
          PsiMethodCallExpression copy = (PsiMethodCallExpression)expression.copy();
          final PsiReferenceExpression methodExpression = copy.getMethodExpression();
          final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
          LOG.assertTrue(parameterList != null);
          for (PsiType mapping : mappings) {
            parameterList.add(factory.createTypeElement(mapping));
          }
          if (methodExpression.getQualifierExpression() == null) {
            final PsiExpression qualifierExpression;
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) return null; // not actual method but some copy in DummyHolder, ignore
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
              qualifierExpression = factory.createReferenceExpression(containingClass);
            }
            else {
              qualifierExpression = RefactoringChangeUtil.createThisExpression(method.getManager(), null);
            }
            methodExpression.setQualifierExpression(qualifierExpression);
          }

          PsiExpression result = withShortening
                                 ? (PsiExpression)JavaCodeStyleManager.getInstance(copy.getProject()).shortenClassReferences(copy)
                                 : copy;

          if (orig != expression) {
            PsiExpression parenthesized = (PsiExpression)orig.copy();
            Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(parenthesized)).replace(result);
            return parenthesized;
          }
          return result;
        }
      }
    }
    return null;
  }

  public static final ArgumentFixerActionFactory REGISTRAR = new AddTypeArgumentsFix.MyFixerActionFactory();
}
