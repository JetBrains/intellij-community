/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class SplitIfAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.SplitIfAction");

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    if (!(token.getParent() instanceof PsiPolyadicExpression)) return false;

    PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
    boolean isAndExpression = expression.getOperationTokenType() == JavaTokenType.ANDAND;
    boolean isOrExpression = expression.getOperationTokenType() == JavaTokenType.OROR;
    if (!isAndExpression && !isOrExpression) return false;

    while (expression.getParent() instanceof PsiPolyadicExpression) {
      expression = (PsiPolyadicExpression)expression.getParent();
      if (isAndExpression && expression.getOperationTokenType() != JavaTokenType.ANDAND) return false;
      if (isOrExpression && expression.getOperationTokenType() != JavaTokenType.OROR) return false;
    }

    if (!(expression.getParent() instanceof PsiIfStatement)) return false;
    PsiIfStatement ifStatement = (PsiIfStatement)expression.getParent();

    if (!PsiTreeUtil.isAncestor(ifStatement.getCondition(), expression, false)) return false;
    if (ifStatement.getThenBranch() == null) return false;

    setText(CodeInsightBundle.message("intention.split.if.text"));

    return true;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.split.if.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    try {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      PsiJavaToken token = (PsiJavaToken)element;
      LOG.assertTrue(token.getTokenType() == JavaTokenType.ANDAND || token.getTokenType() == JavaTokenType.OROR);

      PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);

      LOG.assertTrue(PsiTreeUtil.isAncestor(ifStatement.getCondition(), expression, false));

      if (token.getTokenType() == JavaTokenType.ANDAND) {
        doAndSplit(ifStatement, expression, token, editor);
      }
      else if (token.getTokenType() == JavaTokenType.OROR) {
        doOrSplit(ifStatement, expression, token, editor);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void doAndSplit(PsiIfStatement ifStatement, PsiPolyadicExpression expression, PsiJavaToken token, Editor editor) throws IncorrectOperationException {
    PsiExpression lOperand = getLOperands(expression, token);
    PsiExpression rOperand = getROperands(expression, token);

    PsiManager psiManager = ifStatement.getManager();
    PsiIfStatement subIf = (PsiIfStatement)ifStatement.copy();

    subIf.getCondition().replace(RefactoringUtil.unparenthesizeExpression(rOperand));
    ifStatement.getCondition().replace(RefactoringUtil.unparenthesizeExpression(lOperand));

    if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
      PsiBlockStatement blockStmt =
        (PsiBlockStatement)JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createStatementFromText("{}", null);
      blockStmt = (PsiBlockStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(blockStmt);
      blockStmt = (PsiBlockStatement)ifStatement.getThenBranch().replace(blockStmt);
      blockStmt.getCodeBlock().add(subIf);
    }
    else {
      ifStatement.getThenBranch().replace(subIf);
    }

    int offset1 = ifStatement.getCondition().getTextOffset();

    editor.getCaretModel().moveToOffset(offset1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  private static PsiExpression getROperands(PsiPolyadicExpression expression, PsiJavaToken separator) throws IncorrectOperationException {
    PsiElement next = PsiTreeUtil.skipSiblingsForward(separator, PsiWhiteSpace.class, PsiComment.class);
    final int offsetInParent;
    if (next == null) {
      offsetInParent = separator.getStartOffsetInParent() + separator.getTextLength();
    } else {
      offsetInParent = next.getStartOffsetInParent();
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    String rOperands = expression.getText().substring(offsetInParent);
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  private static PsiExpression getLOperands(PsiPolyadicExpression expression, PsiJavaToken separator) throws IncorrectOperationException {
    PsiElement prev = separator;
    if (prev.getPrevSibling() instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
    if (prev == null) {
      throw new IncorrectOperationException("Unable to split '"+expression.getText()+"' left to '"+separator+"' (offset "+separator.getStartOffsetInParent()+")");
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    String rOperands = expression.getText().substring(0, prev.getStartOffsetInParent());
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  private static void doOrSplit(PsiIfStatement ifStatement, PsiPolyadicExpression expression, PsiJavaToken token, Editor editor) throws IncorrectOperationException {
    PsiExpression lOperand = getLOperands(expression, token);
    PsiExpression rOperand = getROperands(expression, token);

    PsiIfStatement secondIf = (PsiIfStatement)ifStatement.copy();

    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) { elseBranch = (PsiStatement)elseBranch.copy(); }

    ifStatement.getCondition().replace(RefactoringUtil.unparenthesizeExpression(lOperand));
    secondIf.getCondition().replace(RefactoringUtil.unparenthesizeExpression(rOperand));

    ifStatement.setElseBranch(secondIf);
    if (elseBranch != null) { secondIf.setElseBranch(elseBranch); }

    int offset1 = ifStatement.getCondition().getTextOffset();

    editor.getCaretModel().moveToOffset(offset1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }
}
