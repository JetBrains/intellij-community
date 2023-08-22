// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ReplaceWithUnnamedPatternFix extends PsiUpdateModCommandAction<PsiPattern> {
  public ReplaceWithUnnamedPatternFix(@NotNull PsiPattern element) {
    super(element);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiPattern element) {
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.replace.with.unnamed.pattern");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiPattern pattern, @NotNull ModPsiUpdater updater) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    pattern.replace(createUnnamedPattern(factory));
  }

  private static PsiUnnamedPattern createUnnamedPattern(PsiElementFactory factory) {
    PsiInstanceOfExpression expr = (PsiInstanceOfExpression)factory
      .createExpressionFromText("x instanceof R(_)", null);
    PsiDeconstructionPattern pattern = (PsiDeconstructionPattern)Objects.requireNonNull(expr.getPattern());
    return ((PsiUnnamedPattern)pattern.getDeconstructionList().getDeconstructionComponents()[0]);
  }
}
