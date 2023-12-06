// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddParameterListFix extends PsiUpdateModCommandAction<PsiMethod> {
  public AddParameterListFix(@NotNull PsiMethod method) {
    super(method);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod method, @NotNull ModPsiUpdater updater) {
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    method.addAfter(JavaPsiFacade.getElementFactory(context.project())
                      .createParameterList(ArrayUtil.EMPTY_STRING_ARRAY, PsiType.EMPTY_ARRAY), identifier);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("insert.empty.parenthesis");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod method) {
    if (!JavaPsiRecordUtil.isCompactConstructor(method)) return null;
    return Presentation.of(getFamilyName());
  }
}
