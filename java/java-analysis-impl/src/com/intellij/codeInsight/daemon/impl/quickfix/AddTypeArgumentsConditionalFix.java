// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddTypeArgumentsConditionalFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private static final Logger LOG = Logger.getInstance(AddTypeArgumentsConditionalFix.class);

  private final PsiSubstitutor mySubstitutor;
  private final PsiMethod myMethod;

  public AddTypeArgumentsConditionalFix(PsiSubstitutor substitutor, PsiMethodCallExpression expression, PsiMethod method) {
    super(expression);
    mySubstitutor = substitutor;
    myMethod = method;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("add.explicit.type.arguments");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    if (!mySubstitutor.isValid() || !myMethod.isValid()) return null;
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    final PsiTypeParameter[] typeParameters = myMethod.getTypeParameters();
    final String typeArguments = "<" + StringUtil.join(typeParameters, parameter -> {
      final PsiType substituteTypeParam = mySubstitutor.substitute(parameter);
      LOG.assertTrue(substituteTypeParam != null);
      return GenericsUtil.eliminateWildcards(substituteTypeParam).getCanonicalText();
    }, ", ") + ">";
    final PsiExpression expression = call.getMethodExpression().getQualifierExpression();
    String withTypeArgsText;
    if (expression != null) {
      withTypeArgsText = expression.getText();
    }
    else {
      if (isInStaticContext(call, null) || myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass aClass = myMethod.getContainingClass();
        LOG.assertTrue(aClass != null);
        withTypeArgsText = aClass.getQualifiedName();
      }
      else {
        withTypeArgsText = PsiKeyword.THIS;
      }
    }
    withTypeArgsText += "." + typeArguments + call.getMethodExpression().getReferenceName();
    final PsiExpression withTypeArgs = JavaPsiFacade.getElementFactory(context.project())
      .createExpressionFromText(withTypeArgsText + call.getArgumentList().getText(), call);
    call.replace(withTypeArgs);
  }

  public static boolean isInStaticContext(PsiElement element, final @Nullable PsiClass aClass) {
    return PsiUtil.getEnclosingStaticElement(element, aClass) != null;
  }

  public static void register(@NotNull HighlightInfo.Builder highlightInfo, @Nullable PsiExpression expression, @NotNull PsiType lType) {
    if (lType != PsiTypes.nullType() && expression instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      if (thenExpression != null && elseExpression != null) {
        final PsiType thenType = thenExpression.getType();
        final PsiType elseType = elseExpression.getType();
        if (thenType != null && elseType != null) {
          final boolean thenAssignable = TypeConversionUtil.isAssignable(lType, thenType);
          final boolean elseAssignable = TypeConversionUtil.isAssignable(lType, elseType);
          if (!thenAssignable && thenExpression instanceof PsiMethodCallExpression) {
            inferTypeArgs(highlightInfo, lType, thenExpression);
          }
          if (!elseAssignable && elseExpression instanceof PsiMethodCallExpression) {
            inferTypeArgs(highlightInfo, lType, elseExpression);
          }
        }
      }
    }
  }

  private static void inferTypeArgs(@NotNull HighlightInfo.Builder highlightInfo, PsiType lType, PsiExpression thenExpression) {
    final JavaResolveResult result = ((PsiMethodCallExpression)thenExpression).resolveMethodGenerics();
    final PsiMethod method = (PsiMethod)result.getElement();
    if (method != null) {
      final PsiType returnType = method.getReturnType();
      final PsiClass aClass = method.getContainingClass();
      if (returnType != null && aClass != null && aClass.getQualifiedName() != null) {
        final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(method.getProject());
        final PsiDeclarationStatement variableDeclarationStatement =
          javaPsiFacade.getElementFactory().createVariableDeclarationStatement("xxx", lType, thenExpression, thenExpression);
        final PsiExpression initializer =
          ((PsiLocalVariable)variableDeclarationStatement.getDeclaredElements()[0]).getInitializer();
        LOG.assertTrue(initializer != null);

        final PsiSubstitutor substitutor = javaPsiFacade.getResolveHelper()
          .inferTypeArguments(method.getTypeParameters(), method.getParameterList().getParameters(),
                              ((PsiMethodCallExpression)thenExpression).getArgumentList().getExpressions(), PsiSubstitutor.EMPTY,
                              initializer, DefaultParameterTypeInferencePolicy.INSTANCE);
        PsiType substitutedType = substitutor.substitute(returnType);
        if (substitutedType != null && TypeConversionUtil.isAssignable(lType, substitutedType)) {
          highlightInfo.registerFix(new AddTypeArgumentsConditionalFix(substitutor, (PsiMethodCallExpression)thenExpression, method), null,
                                    null, thenExpression.getTextRange(), null);
        }
      }
    }
  }
}
