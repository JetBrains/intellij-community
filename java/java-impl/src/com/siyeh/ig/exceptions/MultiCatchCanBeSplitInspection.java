// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.exceptions;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class MultiCatchCanBeSplitInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SplitMultiCatchVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SplitMultiCatchFix();
  }

  private static void doFixImpl(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiCatchSection catchSection)) {
      return;
    }
    final PsiElement grandParent = catchSection.getParent();
    if (!(grandParent instanceof PsiTryStatement)) {
      return;
    }
    final PsiParameter parameter = catchSection.getParameter();
    if (parameter == null) {
      return;
    }
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiDisjunctionType)) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(element.getProject());
    for (PsiType disjunction : ((PsiDisjunctionType)type).getDisjunctions()) {
      final PsiCatchSection copy = (PsiCatchSection)catchSection.copy();

      final PsiTypeElement typeElement = Objects.requireNonNull(Objects.requireNonNull(copy.getParameter()).getTypeElement());
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(disjunction.getCanonicalText(true), catchSection);
      final PsiElement replaced = new CommentTracker().replaceAndRestoreComments(typeElement, newTypeElement);

      grandParent.addBefore(copy, catchSection);
      styleManager.shortenClassReferences(replaced);
    }

    catchSection.delete();
  }

  private static boolean isAcceptable(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiCatchSection) {
      final PsiType type = ((PsiCatchSection)parent).getCatchType();
      return type instanceof PsiDisjunctionType;
    }
    return false;
  }

  private static class SplitMultiCatchVisitor extends BaseInspectionVisitor {
    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      if (isAcceptable(parameter)) {
        registerError(parameter);
      }
    }

    @Override
    public void visitKeyword(@NotNull PsiKeyword keyword) {
      super.visitKeyword(keyword);
      if (isOnTheFly() && keyword.getTokenType() == JavaTokenType.CATCH_KEYWORD && isAcceptable(keyword)) {
        registerError(keyword);
      }
    }
  }

  private static class SplitMultiCatchFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("multi.catch.can.be.split.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      doFixImpl(startElement);
    }
  }
}
