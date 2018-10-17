// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
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
    PsiSwitchLabelStatement label = ObjectUtils.tryCast(descriptor.getStartElement(), PsiSwitchLabelStatement.class);
    if (label == null) return;
    PsiSwitchStatement statement = label.getEnclosingSwitchStatement();
    if (statement == null) return;
    List<PsiSwitchLabelStatement> labels = PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatement.class);
    for (PsiSwitchLabelStatement otherLabel : labels) {
      if (otherLabel != label) {
        DeleteSwitchLabelFix.deleteLabel(otherLabel);
      }
    }
    new CommentTracker().replaceAndRestoreComments(label, "default:");
    ConvertSwitchToIfIntention.doProcessIntention(statement); // will not create 'if', just unwrap, because only default label is left
  }
}
