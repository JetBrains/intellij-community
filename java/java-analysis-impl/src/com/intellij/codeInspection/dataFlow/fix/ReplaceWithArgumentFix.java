// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithArgumentFix extends PsiUpdateModCommandQuickFix {
  private final String myText;
  private final int myArgNum;

  public ReplaceWithArgumentFix(PsiExpression argument, int argNum) {
    myText = PsiExpressionTrimRenderer.render(argument);
    myArgNum = argNum;
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", myText);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("inspection.redundant.string.replace.with.arg.fix.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (call == null) return;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length <= myArgNum) return;
    new CommentTracker().replaceAndRestoreComments(call, args[myArgNum]);
  }
}
