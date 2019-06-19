// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class DeleteReturnFix implements IntentionAction {

  private final PsiReturnStatement myReturnStatement;
  private final boolean myIsLastStatement;

  @Contract(pure = true)
  public DeleteReturnFix(@NotNull PsiCodeBlock codeBlock, @NotNull PsiReturnStatement statement) {
    myReturnStatement = statement;
    myIsLastStatement = ControlFlowUtils.blockCompletesWithStatement(codeBlock, statement);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    if (!myIsLastStatement) {
      PsiExpression value = myReturnStatement.getReturnValue();
      if (value != null) return QuickFixBundle.message("delete.return.fix.value.text", value.getText());
    }
    return QuickFixBundle.message("delete.return.fix.statement.text");
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("delete.return.fix.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myReturnStatement.isValid() || !BaseIntentionAction.canModify(myReturnStatement)) return false;
    PsiExpression returnValue = myReturnStatement.getReturnValue();
    return returnValue == null || !SideEffectChecker.mayHaveSideEffects(returnValue);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (myIsLastStatement) {
      new CommentTracker().deleteAndRestoreComments(myReturnStatement);
    }
    else {
      PsiElement toDelete = myReturnStatement.getReturnValue();
      new CommentTracker().deleteAndRestoreComments(toDelete == null ? myReturnStatement : toDelete);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
