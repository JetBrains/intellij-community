// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public final class NormalizeBracketsFix extends PsiUpdateModCommandAction<PsiVariable> {
  public NormalizeBracketsFix(PsiVariable variable) {
    super(variable);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiVariable variable, @NotNull ModPsiUpdater updater) {
    JavaSharedImplUtil.normalizeBrackets(variable);
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable element) {
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix");
  }
}
