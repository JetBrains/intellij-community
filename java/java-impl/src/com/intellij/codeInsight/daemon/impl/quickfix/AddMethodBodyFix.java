// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class AddMethodBodyFix extends PsiUpdateModCommandAction<PsiMethod> {
  private final @Nls String myText;

  public AddMethodBodyFix(@NotNull PsiMethod method) {
    super(method);
    myText = QuickFixBundle.message("add.method.body.text");
  }

  public AddMethodBodyFix(@NotNull PsiMethod method, @NotNull @Nls String text) {
    super(method);
    myText = text;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod method) {
    if (method.getBody() != null || method.getContainingClass() == null) return null;
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myText;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod method, @NotNull ModPsiUpdater updater) {
    PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, false);
    PsiClass aClass = method.getContainingClass();
    if (Objects.requireNonNull(aClass).isInterface() &&
        !method.hasModifierProperty(PsiModifier.STATIC) &&
        !method.hasModifierProperty(PsiModifier.DEFAULT) &&
        !method.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, true);
    }
    CreateFromUsageUtils.setupMethodBody(method, updater);
    if (method.getContainingFile().getOriginalFile() == context.file()) {
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        CreateFromUsageUtils.setupEditor(body, updater);
      }
    }
  }
}
