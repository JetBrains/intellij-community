// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ReplaceCaseDefaultWithDefaultFix extends PsiUpdateModCommandAction<PsiCaseLabelElementList> {

  public ReplaceCaseDefaultWithDefaultFix(@NotNull PsiCaseLabelElementList list) {
    super(list);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("replace.case.default.with.default");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiCaseLabelElementList list, @NotNull ModPsiUpdater updater) {
    PsiCaseLabelElement[] elements = list.getElements();
    if (elements.length != 1 || !(elements[0] instanceof PsiDefaultCaseLabelElement)) {
      return;
    }
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiSwitchLabelStatementBase caseLabels)) {
      return;
    }
    String dummyText;
    CommentTracker tracker = new CommentTracker();
    if (parent instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
      dummyText = "default -> " + (ruleStatement.getBody() != null ? ruleStatement.getBody().getText() : "");
      tracker.markUnchanged(ruleStatement.getBody());
    }
    else {
      dummyText = "default:" ;
    }
    PsiStatement dummy = JavaPsiFacade.getElementFactory(context.project()).createStatementFromText(dummyText, list);
    PsiElement replaced = tracker.replace(caseLabels, dummy);
    tracker.insertCommentsBefore(replaced);
  }
}
