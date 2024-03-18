// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.varargs;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class WrapVarargArgumentsWithExplicitArrayIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("wrap.vararg.arguments.with.explicit.array.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("wrap.vararg.arguments.with.explicit.array.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new VarargArgumentsPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiCall call = PsiTreeUtil.getParentOfType(element, PsiCall.class);
    if (call == null) {
      return;
    }
    final PsiMethod method = call.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final int parametersCount = parameterList.getParametersCount();
    final PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) {
      return;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    final StringBuilder newExpressionText = new StringBuilder("new ");
    final PsiParameter[] parameters = parameterList.getParameters();
    final int varargParameterIndex = parametersCount - 1;
    final PsiType componentType = PsiTypesUtil.getParameterType(parameters, varargParameterIndex, true);
    final JavaResolveResult resolveResult = call.resolveMethodGenerics();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiType type = substitutor.substitute(componentType);
    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getLowerBound();
    }
    newExpressionText.append(JavaGenericsUtil.isReifiableType(type)
                             ? type.getCanonicalText()
                             : TypeConversionUtil.erasure(type).getCanonicalText());
    newExpressionText.append("[]{");
    if (arguments.length > varargParameterIndex) {
      final PsiExpression argument1 = arguments[varargParameterIndex];
      argument1.delete();
      newExpressionText.append(argument1.getText());
      for (int i = parametersCount; i < arguments.length; i++) {
        final PsiExpression argument = arguments[i];
        newExpressionText.append(',').append(argument.getText());
        argument.delete();
      }
    }
    newExpressionText.append("}");
    final Project project = element.getProject();
    final PsiExpression newExpression =
      JavaPsiFacade.getElementFactory(project).createExpressionFromText(newExpressionText.toString(), element);
    CodeStyleManager.getInstance(project).reformat(argumentList.add(newExpression));
  }
}