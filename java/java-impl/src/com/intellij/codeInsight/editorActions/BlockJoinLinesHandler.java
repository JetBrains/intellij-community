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

public class BlockJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(BlockJoinLinesHandler.class);

  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile psiFile, final int start, final int end) {
    PsiElement elementAtStartLineEnd = psiFile.findElementAt(start);
    PsiElement elementAtNextLineStart = psiFile.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;
    if (!PsiUtil.isJavaToken(elementAtStartLineEnd, JavaTokenType.LBRACE)) {
      return -1;
    }
    final PsiElement codeBlock = elementAtStartLineEnd.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return -1;
    if (!(codeBlock.getParent() instanceof PsiBlockStatement)) return -1;
    final PsiElement parentStatement = codeBlock.getParent().getParent();

    if (getForceBraceSetting(parentStatement) == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS) {
      return CANNOT_JOIN;
    }
    PsiElement foundStatement = null;
    for (PsiElement element = elementAtStartLineEnd.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiWhiteSpace) continue;
      if (PsiUtil.isJavaToken(element, JavaTokenType.RBRACE) && element.getParent() == codeBlock) {
        if (foundStatement == null) return -1;
        break;
      }
      if (foundStatement != null) return -1;
      foundStatement = element;
    }
    if (!(foundStatement instanceof PsiStatement)) return -1;
    PsiElement parent = codeBlock.getParent();
    if (isPotentialShortIf(foundStatement) && parent instanceof PsiBlockStatement) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiIfStatement &&
          ((PsiIfStatement)grandParent).getThenBranch() == parent &&
          ((PsiIfStatement)grandParent).getElseBranch() != null) {
        /*
         like "if(...) {if(...){...}} else {...}"
         unwrapping the braces of outer 'if' then-branch will cause semantics change
         */
        return -1;
      }
    }
    try {
      final PsiElement newStatement = parent.replace(foundStatement);

      return newStatement.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return -1;
  }

  private static boolean isPotentialShortIf(PsiElement statement) {
    while (true) {
      // JLS 14.5
      if (statement instanceof PsiLabeledStatement) {
        statement = ((PsiLabeledStatement)statement).getStatement();
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
