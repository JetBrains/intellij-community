// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class DeleteReturnFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final SmartPsiElementPointer<PsiReturnStatement> myStatementPtr;
  private final SmartPsiElementPointer<PsiExpression> myValuePtr;
  private final boolean myIsLastStatement;
  private final boolean myHasSideEffects;

  public DeleteReturnFix(@NotNull PsiMethod method, @NotNull PsiReturnStatement returnStatement, @NotNull PsiExpression returnValue) {
    super(returnStatement);
    PsiCodeBlock codeBlock = Objects.requireNonNull(method.getBody());
    SmartPointerManager manager = SmartPointerManager.getInstance(returnStatement.getProject());
    myStatementPtr = manager.createSmartPsiElementPointer(returnStatement);
    myValuePtr = manager.createSmartPsiElementPointer(returnValue);
    myIsLastStatement = ControlFlowUtils.blockCompletesWithStatement(codeBlock, returnStatement);
    myHasSideEffects = SideEffectChecker.mayHaveSideEffects(returnValue);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    String toDelete = myIsLastStatement ? "statement" : "value";
    return QuickFixBundle.message(myHasSideEffects ? "delete.return.fix.side.effects.text" : "delete.return.fix.text", toDelete);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("delete.return.fix.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiReturnStatement returnStatement = myStatementPtr.getElement();
    if (returnStatement == null) return;
    PsiExpression returnValue = myValuePtr.getElement();
    if (returnValue == null) return;
    CommentTracker ct = new CommentTracker();
    if (myHasSideEffects) {
      returnValue = Objects.requireNonNull(RefactoringUtil.ensureCodeBlock(returnValue));
      returnStatement = (PsiReturnStatement)returnValue.getParent();
    }
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(returnValue);
    sideEffects.forEach(ct::markUnchanged);
    PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, returnValue);
    if (statements.length > 0) BlockUtils.addBefore(returnStatement, statements);
    PsiElement toDelete = myIsLastStatement ? returnStatement : returnValue;
    ct.deleteAndRestoreComments(toDelete);
  }
}
