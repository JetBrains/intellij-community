// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ReverseCaseDefaultNullFix extends PsiUpdateModCommandAction<PsiCaseLabelElementList> {

  public ReverseCaseDefaultNullFix(@NotNull PsiCaseLabelElementList list) {
    super(list);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("replace.case.default.null.with.null.default");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiCaseLabelElementList list, @NotNull ModPsiUpdater updater) {
    PsiCaseLabelElement[] elements = list.getElements();
    if (elements.length != 2 ||
        !(elements[0] instanceof PsiDefaultCaseLabelElement &&
          elements[1] instanceof PsiLiteralExpression literalExpression && ExpressionUtils.isNullLiteral(literalExpression)) ) {
      return;
    }
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiSwitchLabelStatementBase caseLabels)) {
      return;
    }
    String dummyText;
    CommentTracker tracker = new CommentTracker();
    if (parent instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
      dummyText = "case null, default -> " + (ruleStatement.getBody() != null ? ruleStatement.getBody().getText() : "");
      tracker.markUnchanged(ruleStatement.getBody());
    }
    else {
      dummyText = "case null, default:" ;
    }
    PsiStatement dummy = JavaPsiFacade.getElementFactory(context.project()).createStatementFromText(dummyText, list);
    PsiElement replaced = tracker.replace(caseLabels, dummy);
    tracker.insertCommentsBefore(replaced);
  }
}
