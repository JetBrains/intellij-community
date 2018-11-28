// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.BreakConverter;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

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
    PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
    if (labelStatement == null) return;
    PsiSwitchStatement statement = labelStatement.getEnclosingSwitchStatement();
    if (statement == null) return;
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatementBase.class);
    for (PsiSwitchLabelStatementBase otherLabel : labels) {
      if (otherLabel != labelStatement) {
        DeleteSwitchLabelFix.deleteLabel(otherLabel);
      }
    }
    BreakConverter converter = BreakConverter.from(statement);
    if (converter == null) return;
    converter.process();
    unwrap(labelStatement, statement);
  }

  private static void unwrap(PsiSwitchLabelStatementBase labelStatement, PsiSwitchStatement statement) {
    PsiCodeBlock block = statement.getBody();
    PsiStatement body =
      labelStatement instanceof PsiSwitchLabeledRuleStatement ? ((PsiSwitchLabeledRuleStatement)labelStatement).getBody() : null;
    if (body == null) {
      new CommentTracker().deleteAndRestoreComments(labelStatement);
    }
    else if (body instanceof PsiBlockStatement) {
      block = ((PsiBlockStatement)body).getCodeBlock();
    }
    else {
      new CommentTracker().replaceAndRestoreComments(labelStatement, body);
    }
    PsiCodeBlock parent = ObjectUtils.tryCast(statement.getParent(), PsiCodeBlock.class);
    CommentTracker ct = new CommentTracker();
    if (parent != null && !BlockUtils.containsConflictingDeclarations(Objects.requireNonNull(block), parent)) {
      ct.grabComments(statement);
      ct.markUnchanged(block);
      ct.insertCommentsBefore(statement);
      BlockUtils.inlineCodeBlock(statement, block);
    }
    else if (block != null) {
      ct.replaceAndRestoreComments(statement, ct.text(block));
    }
    else {
      ct.deleteAndRestoreComments(statement);
    }
  }
}
