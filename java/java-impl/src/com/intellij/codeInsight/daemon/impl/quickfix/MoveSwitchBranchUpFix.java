// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveSwitchBranchUpFix extends PsiUpdateModCommandAction<PsiCaseLabelElement> {
  private final String myBeforeName;
  private final String myName;
  private final @NotNull SmartPsiElementPointer<PsiCaseLabelElement> myLabelElementPointer;

  public MoveSwitchBranchUpFix(@NotNull PsiCaseLabelElement moveBeforeLabel,
                               @NotNull PsiCaseLabelElement labelElement) {
    super(moveBeforeLabel);
    myLabelElementPointer = SmartPointerManager.createPointer(labelElement);
    myBeforeName = moveBeforeLabel.getText();
    myName = labelElement.getText();
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiCaseLabelElement element, @NotNull ModPsiUpdater updater) {
    PsiCaseLabelElement labelElement = updater.getWritable(myLabelElementPointer.getElement());
    if (labelElement == null) return;
    PsiSwitchLabelStatementBase moveBeforeLabel = PsiImplUtil.getSwitchLabel(element);
    PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(labelElement);
    if (moveBeforeLabel == null || labelStatement == null) return;
    PsiCodeBlock scope = ObjectUtils.tryCast(moveBeforeLabel.getParent(), PsiCodeBlock.class);
    if (scope == null) return;
    PsiSwitchLabelStatementBase nextLabel = PsiTreeUtil.getNextSiblingOfType(labelStatement, PsiSwitchLabelStatementBase.class);
    PsiElement afterLast = nextLabel == null ? scope.getRBrace() : nextLabel;
    if (afterLast == null) return;
    PsiElement last = afterLast.getPrevSibling();
    scope.addRangeBefore(labelStatement, last, moveBeforeLabel);
    scope.deleteChildRange(labelStatement, last);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiCaseLabelElement element) {
    return Presentation.of(QuickFixBundle.message("move.switch.branch.up.text", myName, myBeforeName));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("move.switch.branch.up.family");
  }
}
