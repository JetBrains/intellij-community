// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UnwrapSwitchLabelFix implements LocalQuickFix {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.unwrap.statement", PsiKeyword.SWITCH);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression label = ObjectUtils.tryCast(descriptor.getStartElement(), PsiExpression.class);
    if (label == null) return;
    PsiSwitchLabelStatementBase labelStatement = SwitchUtils.getLabelStatementForLabel(label);
    if (labelStatement == null) return;
    PsiSwitchStatement statement = labelStatement.getEnclosingSwitchStatement();
    if (statement == null) return;
    List<PsiSwitchLabelStatement> labels = PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatement.class);
    for (PsiSwitchLabelStatement otherLabel : labels) {
      if (otherLabel != labelStatement) {
        DeleteSwitchLabelFix.deleteLabel(otherLabel);
      }
    }
    new CommentTracker().replaceAndRestoreComments(labelStatement, "default:");
    ConvertSwitchToIfIntention.doProcessIntention(statement); // will not create 'if', just unwrap, because only default label is left
  }
}
