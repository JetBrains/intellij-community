// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;

public class AddMainMethodFix extends PsiUpdateModCommandAction<PsiImplicitClass> {
  public AddMainMethodFix(@NotNull PsiImplicitClass element) {
    super(element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.add.main.method");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiImplicitClass element, @NotNull ModPsiUpdater updater) {
    PsiMethod mainMethod = JavaPsiFacade.getInstance(context.project()).getElementFactory().createMethod("main", PsiTypes.voidType());
    element.add(mainMethod);
  }
}
