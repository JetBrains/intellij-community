// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DeleteRepeatedInterfaceFix extends PsiUpdateModCommandAction<PsiTypeElement> {
  public DeleteRepeatedInterfaceFix(PsiTypeElement conjunct) {
    super(conjunct);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("delete.repeated.interface");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeElement conjunct) {
    return Presentation.of(JavaAnalysisBundle.message("delete.repeated.0", conjunct.getText())).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeElement conjunct, @NotNull ModPsiUpdater updater) {
    PsiTypeElement[] conjList = PsiTreeUtil.getChildrenOfType(conjunct.getParent(), PsiTypeElement.class);
    if (conjList == null) return;
    final PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(conjunct, PsiTypeCastExpression.class);
    if (castExpression == null) return;
    final PsiTypeElement castType = castExpression.getCastType();
    if (castType == null) return;
    final PsiType type = castType.getType();
    if (!(type instanceof PsiIntersectionType)) return;
    final String typeText = StreamEx.of(conjList).without(conjunct).map(PsiElement::getText).joining(" & ");
    Project project = context.project();
    final PsiTypeCastExpression newCastExpr =
      (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("(" + typeText + ")a", castType);
    CodeStyleManager.getInstance(project).reformat(castType.replace(Objects.requireNonNull(newCastExpr.getCastType())));
  }
}
