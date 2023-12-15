// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.trivialif;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public final class ConvertToNestedIfIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.to.nested.if.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("convert.to.nested.if.intention.name");
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {

      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiReturnStatement returnStatement)) {
          return false;
        }
        final PsiExpression returnValue = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
        if (!(returnValue instanceof PsiPolyadicExpression polyadicExpression)) {
          return false;
        }
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        return tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR;
      }
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null || ErrorUtil.containsDeepError(returnValue)) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final String newStatementText = buildIf(returnValue, true, tracker, new StringBuilder()).toString();
    final Project project = returnStatement.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiBlockStatement blockStatement = (PsiBlockStatement)elementFactory.createStatementFromText("{" + newStatementText + "}", returnStatement);
    final PsiElement parent = returnStatement.getParent();
    if (parent instanceof PsiCodeBlock) {
      PsiStatement @NotNull [] statements = blockStatement.getCodeBlock().getStatements();
      for (int i = 0; i < statements.length; i++) {
        PsiStatement st = statements[i];
        PsiElement result = CodeStyleManager.getInstance(project).reformat(parent.addBefore(st, returnStatement));
        if (i == 0) {
          updater.moveCaretTo(result);
        }
      }
      PsiReplacementUtil.replaceStatement(returnStatement, "return false;", tracker);
    }
    else {
      blockStatement.getCodeBlock().add(elementFactory.createStatementFromText("return false;", returnStatement));
      PsiBlockStatement result = (PsiBlockStatement)tracker.replaceAndRestoreComments(returnStatement, blockStatement);
      updater.moveCaretTo(result.getCodeBlock().getStatements()[0]);
    }
  }

  private static StringBuilder buildIf(@Nullable PsiExpression expression,
                                       boolean top,
                                       CommentTracker tracker,
                                       StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, false, tracker, out);
        }
        if (top && !StringUtil.endsWith(out, "return true;")) {
          out.append("return true;");
        }
        return out;
      }
      else if (top && JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, false, tracker, out);
          if (!StringUtil.endsWith(out, "return true;")) {
            out.append("return true;");
          }
        }
        return out;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      buildIf(parenthesizedExpression.getExpression(), top, tracker, out);
      return out;
    }
    if (expression != null) {
      out.append("if(").append(tracker.text(expression)).append(")");
    }
    return out;
  }
}
