// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveSwitchBranchUpFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myBeforeName;
  private final String myName;

  public MoveSwitchBranchUpFix(PsiCaseLabelElement moveBeforeLabel,
                               PsiCaseLabelElement labelElement) {
    super(moveBeforeLabel, labelElement);
    myBeforeName = moveBeforeLabel.getText();
    myName = labelElement.getText();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiSwitchLabelStatementBase moveBeforeLabel = PsiImplUtil.getSwitchLabel((PsiCaseLabelElement)startElement);
    PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel((PsiCaseLabelElement)endElement);
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
  public @NotNull String getText() {
    return QuickFixBundle.message("move.switch.branch.up.text", myName, myBeforeName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("move.switch.branch.up.family");
  }
}
