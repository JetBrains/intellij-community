// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlipIntersectionSidesFix extends PsiUpdateModCommandAction<PsiTypeElement> {
  private static final Logger LOG = Logger.getInstance(FlipIntersectionSidesFix.class);
  private final String myClassName;
  private final PsiTypeElement myConjunct;

  public FlipIntersectionSidesFix(String className,
                                  PsiTypeElement conjunct,
                                  PsiTypeElement castTypeElement) {
    super(castTypeElement);
    myClassName = className;
    myConjunct = conjunct;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("move.to.front");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeElement castTypeElement) {
    if (!myConjunct.isValid()) return null;
    PsiTypeElement firstChild = PsiTreeUtil.findChildOfType(castTypeElement, PsiTypeElement.class);
    if (firstChild == null || myConjunct.textMatches(firstChild.getText())) return null;
    return Presentation.of(JavaAnalysisBundle.message("move.0.to.the.beginning", myClassName)).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeElement castTypeElement, @NotNull ModPsiUpdater updater) {
    PsiTypeElement[] conjuncts = PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class);
    if (conjuncts == null) return;
    PsiTypeElement conjunct = updater.getWritable(myConjunct);
    final String intersectionTypeText =
      StreamEx.of(conjuncts).without(conjunct).prepend(conjunct).map(PsiElement::getText).joining(" & ");
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.project());
    final PsiTypeCastExpression fixedCast =
      (PsiTypeCastExpression)elementFactory.createExpressionFromText("(" + intersectionTypeText + ") a", castTypeElement);
    final PsiTypeElement fixedCastCastType = fixedCast.getCastType();
    LOG.assertTrue(fixedCastCastType != null);
    final PsiElement flippedTypeElement = castTypeElement.replace(fixedCastCastType);
    CodeStyleManager.getInstance(context.project()).reformat(flippedTypeElement);
  }
}
