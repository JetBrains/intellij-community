// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.psiutils.FieldAccessFixer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceInaccessibleFieldWithGetterSetterFix extends PsiUpdateModCommandAction<PsiReferenceExpression> {
  @NotNull private final FieldAccessFixer myFixer;

  public ReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiReferenceExpression ref, @NotNull FieldAccessFixer fixer) {
    super(ref);
    myFixer = fixer;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression place, @NotNull ModPsiUpdater updater) {
    myFixer.apply(place);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression element) {
    String message = myFixer.setter() ? QuickFixBundle.message("replace.with.setter") : QuickFixBundle.message("replace.with.getter");
    return Presentation.of(message).withFixAllOption(this);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.with.getter.setter");
  }
}
