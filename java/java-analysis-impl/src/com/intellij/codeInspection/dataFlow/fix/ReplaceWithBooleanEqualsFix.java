// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithBooleanEqualsFix extends PsiUpdateModCommandQuickFix {
  private final String myOldExprText;
  private final boolean myFalseIsAcceptable;

  public ReplaceWithBooleanEqualsFix(@NotNull PsiExpression qualifier) {
    myOldExprText = qualifier.getText();
    PsiPrefixExpression parent = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(qualifier.getParent()), PsiPrefixExpression.class);
    myFalseIsAcceptable = parent != null && parent.getOperationTokenType() == JavaTokenType.EXCL;
  }

  @Override
  public @NotNull String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", createNewExprText());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("replace.with.boolean.equals");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiElement qualifier = ObjectUtils.tryCast(element, PsiExpression.class);
    if (qualifier == null) return;
    if (myFalseIsAcceptable) {
      qualifier = qualifier.getParent();
    }
    PsiExpression oldExpr = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(qualifier), PsiExpression.class);
    if (oldExpr == null) return;
    PsiReplacementUtil.replaceExpression(oldExpr, createNewExprText(), new CommentTracker());
  }

  @NotNull
  private String createNewExprText() {
    return "Boolean." + (myFalseIsAcceptable ? "FALSE" : "TRUE") + ".equals(" + myOldExprText + ")";
  }
}
