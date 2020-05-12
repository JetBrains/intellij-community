// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithArgumentFix implements LocalQuickFix {
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
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
    if (call == null) return;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length <= myArgNum) return;
    new CommentTracker().replaceAndRestoreComments(call, args[myArgNum]);
  }
}
