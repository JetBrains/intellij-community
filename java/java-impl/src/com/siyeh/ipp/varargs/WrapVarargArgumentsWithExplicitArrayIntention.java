// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.varargs;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.jdk.VarargParameterInspection;
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
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new VarargArgumentsPredicate();
  }

  @Override
  protected void invoke(@NotNull PsiElement element) {
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
    final PsiParameter varargParameter = parameterList.getParameter(parametersCount - 1);
    if (varargParameter == null) {
      return;
    }
    VarargParameterInspection.modifyCall(varargParameter, parametersCount - 1, call);
  }
}