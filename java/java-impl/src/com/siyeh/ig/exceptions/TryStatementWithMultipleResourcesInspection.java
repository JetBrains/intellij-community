// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.exceptions;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class TryStatementWithMultipleResourcesInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SplitTryWithResourcesVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SplitTryWithResourcesFix();
  }

  private static void doFixImpl(@NotNull PsiElement element) {
    final PsiTryStatement tryStatement = (PsiTryStatement)element.getParent();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null || resourceList.getResourceVariablesCount() <= 1) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    @NonNls final StringBuilder newTryStatementText = new StringBuilder();
    int count = 0;
    for (PsiResourceListElement resource : resourceList) {
      if (count > 0) {
        newTryStatementText.append("{\n");
      }
      ++count;
      newTryStatementText.append("try (").append(tracker.text(resource)).append(")");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    newTryStatementText.append(tracker.text(tryBlock));
    for (int i = 1; i < count; i++) {
      newTryStatementText.append("\n}");
    }
    final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection catchSection : catchSections) {
      newTryStatementText.append(tracker.text(catchSection));
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      newTryStatementText.append("finally").append(tracker.text(finallyBlock));
    }
    PsiReplacementUtil.replaceStatement(tryStatement, newTryStatementText.toString(), tracker);
  }

  private static boolean isAcceptable(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiTryStatement tryStatement)) {
      return false;
    }
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return false;
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return false;
    }
    return resourceList.getResourceVariablesCount() > 1;
  }

  private static class SplitTryWithResourcesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitKeyword(@NotNull PsiKeyword keyword) {
      super.visitKeyword(keyword);
      if (isOnTheFly() && keyword.getTokenType() == JavaTokenType.TRY_KEYWORD && isAcceptable(keyword)) {
        registerError(keyword);
      }
    }

    @Override
    public void visitResourceList(@NotNull PsiResourceList resourceList) {
      super.visitResourceList(resourceList);
      if (isAcceptable(resourceList)) {
        registerError(resourceList);
      }
    }
  }

  private static class SplitTryWithResourcesFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      doFixImpl(startElement);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("try.statement.with.multiple.resources.quickfix");
    }
  }
}
