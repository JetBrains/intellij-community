// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class BlockJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(BlockJoinLinesHandler.class);

  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile psiFile, int start, int end) {
    PsiElement elementAtStartLineEnd = psiFile.findElementAt(start);
    PsiElement elementAtNextLineStart = psiFile.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return CANNOT_JOIN;
    if (!PsiUtil.isJavaToken(elementAtStartLineEnd, JavaTokenType.LBRACE)) return CANNOT_JOIN;
    final PsiElement codeBlock = elementAtStartLineEnd.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return CANNOT_JOIN;
    PsiElement parent = codeBlock.getParent();
    if (parent instanceof PsiLambdaExpression) {
      PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(codeBlock);
      if (expression != null) {
        PsiElement newElement = codeBlock.replace(expression);
        return newElement.getTextRange().getStartOffset();
      }
    }
    if (!(parent instanceof PsiBlockStatement)) return CANNOT_JOIN;
    final PsiElement parentStatement = parent.getParent();

    if (getForceBraceSetting(parentStatement) == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS) {
      return CANNOT_JOIN;
    }
    PsiElement foundStatement = null;
    for (PsiElement element = elementAtStartLineEnd.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiWhiteSpace) continue;
      if (PsiUtil.isJavaToken(element, JavaTokenType.RBRACE) && element.getParent() == codeBlock) {
        if (foundStatement == null) return CANNOT_JOIN;
        break;
      }
      if (foundStatement != null) return CANNOT_JOIN;
      foundStatement = element;
    }
    if (!(foundStatement instanceof PsiStatement)) return CANNOT_JOIN;
    if (isPotentialShortIf(foundStatement) &&
        parent.getParent() instanceof PsiIfStatement ifStatement &&
        ifStatement.getThenBranch() == parent &&
        ifStatement.getElseBranch() != null) {
        /*
         like "if(...) {if(...){...}} else {...}"
         unwrapping the braces of outer 'if' then-branch will cause semantics change
         */
      return CANNOT_JOIN;
    }
    if (parentStatement instanceof PsiSwitchLabeledRuleStatement && !(foundStatement instanceof PsiExpressionStatement)) return CANNOT_JOIN;
    try {
      final PsiElement newStatement = parent.replace(foundStatement);
      return newStatement.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return CANNOT_JOIN;
  }

  private static boolean isPotentialShortIf(PsiElement statement) {
    while (true) {
      // JLS 14.5
      if (statement instanceof PsiLabeledStatement labeled) {
        statement = labeled.getStatement();
      }
      else if (statement instanceof PsiForStatement || statement instanceof PsiForeachStatement || statement instanceof PsiWhileStatement) {
        statement = ((PsiLoopStatement)statement).getBody();
      }
      else break;
    }
    return statement instanceof PsiIfStatement;
  }

  private static int getForceBraceSetting(PsiElement statement) {
    CodeStyleSettings settings = CodeStyle.getSettings(statement.getContainingFile());
    final CommonCodeStyleSettings codeStyleSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    if (statement instanceof PsiIfStatement) {
      return codeStyleSettings.IF_BRACE_FORCE;
    }
    if (statement instanceof PsiWhileStatement) {
      return codeStyleSettings.WHILE_BRACE_FORCE;
    }
    if (statement instanceof PsiForStatement) {
      return codeStyleSettings.FOR_BRACE_FORCE;
    }
    if (statement instanceof PsiDoWhileStatement) {
      return codeStyleSettings.DOWHILE_BRACE_FORCE;
    }
    return CommonCodeStyleSettings.DO_NOT_FORCE;
  }
}
