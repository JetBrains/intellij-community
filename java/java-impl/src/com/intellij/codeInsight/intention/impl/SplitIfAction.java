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
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getLOperands;
import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getROperands;

/**
 * @author mike
 */
public class SplitIfAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.SplitIfAction");

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiPolyadicExpression expression = SplitConditionUtil.findCondition(element);
    if (expression == null) return false;

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiIfStatement)) return false;
    PsiIfStatement ifStatement = (PsiIfStatement)parent;

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
    PsiJavaToken token = (PsiJavaToken)element;
    LOG.assertTrue(token.getTokenType() == JavaTokenType.ANDAND || token.getTokenType() == JavaTokenType.OROR);

    PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
    if (ifStatement == null) return;

    PsiExpression condition = ifStatement.getCondition();
    LOG.assertTrue(PsiTreeUtil.isAncestor(condition, expression, false));

    CommentTracker ct = new CommentTracker();
    PsiExpression lOperand = getLOperands(expression, token, ct);
    PsiExpression rOperand = getROperands(expression, token, ct);
    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();

    PsiIfStatement replacement =
      SplitConditionUtil.create(factory, ifStatement, lOperand, rOperand, token.getTokenType(), ct);
    if (replacement == null) return;
    PsiElement result = ct.replaceAndRestoreComments(ifStatement, replacement);
    result = CodeStyleManager.getInstance(project).reformat(result);
    if (result instanceof PsiIfStatement) {
      PsiExpression resultCondition = ((PsiIfStatement)result).getCondition();
      if (resultCondition != null) {
        int offset = resultCondition.getTextOffset();

        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().removeSelection();
      }
    }
  }
}
