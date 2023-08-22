// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveCatchUpFix implements ModCommandAction {
  private final @NotNull PsiCatchSection myCatchSection;
  private final @NotNull PsiCatchSection myMoveBeforeSection;

  public MoveCatchUpFix(@NotNull PsiCatchSection catchSection, @NotNull PsiCatchSection moveBeforeSection) {
    this.myCatchSection = catchSection;
    myMoveBeforeSection = moveBeforeSection;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (myCatchSection.isValid()
        && BaseIntentionAction.canModify(myCatchSection)
        && myMoveBeforeSection.isValid()
        && myCatchSection.getCatchType() != null
        && PsiUtil.resolveClassInType(myCatchSection.getCatchType()) != null
        && myMoveBeforeSection.getCatchType() != null
        && PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()) != null
        && !myCatchSection.getManager().areElementsEquivalent(
      PsiUtil.resolveClassInType(myCatchSection.getCatchType()),
      PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()))) {
      return Presentation.of(QuickFixBundle.message("move.catch.up.text",
                                                    JavaHighlightUtil.formatType(myCatchSection.getCatchType()),
                                                    JavaHighlightUtil.formatType(myMoveBeforeSection.getCatchType())));
    }
    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.catch.up.family");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(context, updater -> {
      PsiCatchSection catchSection = updater.getWritable(myCatchSection);
      PsiCatchSection moveBeforeSection = updater.getWritable(myMoveBeforeSection);
      PsiTryStatement statement = catchSection.getTryStatement();
      statement.addBefore(catchSection, moveBeforeSection);
      catchSection.delete();
    });
  }
}
