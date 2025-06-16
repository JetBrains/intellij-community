// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

public class AddTypeArgumentsConditionalFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private final @NotNull PsiSubstitutor mySubstitutor;

  private AddTypeArgumentsConditionalFix(@NotNull PsiSubstitutor substitutor, @NotNull PsiMethodCallExpression expression) {
    super(expression);
    mySubstitutor = substitutor;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("add.explicit.type.arguments");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    if (!mySubstitutor.isValid()) return null;
    String name = getFamilyName();
    if (PsiUtil.skipParenthesizedExprUp(call.getParent()) instanceof PsiConditionalExpression conditional) {
      if (PsiTreeUtil.isAncestor(conditional.getThenExpression(), call, false)) {
        name = JavaAnalysisBundle.message("add.explicit.type.arguments.then");
      } else if (PsiTreeUtil.isAncestor(conditional.getElseExpression(), call, false)) {
        name = JavaAnalysisBundle.message("add.explicit.type.arguments.else");
      }
    }
    return Presentation.of(name).withPriority(PriorityAction.Priority.HIGH)
      .withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return;
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    final String typeArguments = "<" + StringUtil.join(typeParameters, parameter -> {
      final PsiType substituteTypeParam = Objects.requireNonNull(mySubstitutor.substitute(parameter));
      return GenericsUtil.eliminateWildcards(substituteTypeParam).getCanonicalText();
    }, ", ") + ">";
    final PsiExpression expression = call.getMethodExpression().getQualifierExpression();
    String withTypeArgsText;
    if (expression != null) {
      withTypeArgsText = expression.getText();
    }
    else {
      if (isInStaticContext(call, null) || method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        withTypeArgsText = aClass.getQualifiedName();
      }
      else {
        withTypeArgsText = JavaKeywords.THIS;
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

  public static void register(@NotNull Consumer<? super CommonIntentionAction> highlightInfo, @Nullable PsiExpression expression, @NotNull PsiType lType) {
    if (lType != PsiTypes.nullType() && expression instanceof PsiConditionalExpression conditional) {
      PsiExpression thenExpression = PsiUtil.skipParenthesizedExprDown(conditional.getThenExpression());
      PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(conditional.getElseExpression());
      if (thenExpression != null && elseExpression != null) {
        PsiType thenType = thenExpression.getType();
        PsiType elseType = elseExpression.getType();
        if (thenType != null && elseType != null) {
          boolean thenAssignable = TypeConversionUtil.isAssignable(lType, thenType);
          boolean elseAssignable = TypeConversionUtil.isAssignable(lType, elseType);
          if (!thenAssignable && thenExpression instanceof PsiMethodCallExpression call) {
            inferTypeArgs(highlightInfo, lType, call);
          }
          if (!elseAssignable && elseExpression instanceof PsiMethodCallExpression call) {
            inferTypeArgs(highlightInfo, lType, call);
          }
        }
      }
    }
  }

  private static void inferTypeArgs(@NotNull Consumer<? super ModCommandAction> fixConsumer, 
                                    @NotNull PsiType lType, 
                                    @NotNull PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method != null) {
      PsiType returnType = method.getReturnType();
      PsiClass aClass = method.getContainingClass();
      if (returnType != null && aClass != null && aClass.getQualifiedName() != null) {
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(method.getProject());
        PsiDeclarationStatement variableDeclarationStatement =
          javaPsiFacade.getElementFactory().createVariableDeclarationStatement("xxx", lType, call, call);
        PsiExpression initializer =
          Objects.requireNonNull(((PsiLocalVariable)variableDeclarationStatement.getDeclaredElements()[0]).getInitializer());

        PsiSubstitutor substitutor = javaPsiFacade.getResolveHelper()
          .inferTypeArguments(method.getTypeParameters(), method.getParameterList().getParameters(),
                              call.getArgumentList().getExpressions(), PsiSubstitutor.EMPTY,
                              initializer, DefaultParameterTypeInferencePolicy.INSTANCE);
        PsiType substitutedType = substitutor.substitute(returnType);
        if (substitutedType != null && TypeConversionUtil.isAssignable(lType, substitutedType)) {
          fixConsumer.accept(new AddTypeArgumentsConditionalFix(substitutor, call));
        }
      }
    }
  }
}
