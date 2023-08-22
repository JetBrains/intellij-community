// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class RemoveParameterListFix extends PsiUpdateModCommandAction<PsiMethod> {
  public RemoveParameterListFix(@NotNull PsiMethod method) {
    super(method);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.parameter.list");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod method, @NotNull ModPsiUpdater updater) {
    final PsiMethod emptyMethod = JavaPsiFacade.getElementFactory(context.project()).createMethodFromText("void foo(){}", method);
    method.getParameterList().replace(emptyMethod.getParameterList());
  }
}
