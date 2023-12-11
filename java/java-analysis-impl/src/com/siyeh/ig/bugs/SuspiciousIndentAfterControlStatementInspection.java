// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SuspiciousIndentAfterControlStatementInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiStatement statement = (PsiStatement)infos[0];
    final PsiElement token = statement.getFirstChild();
    return InspectionGadgetsBundle.message("suspicious.indent.after.control.statement.problem.descriptor", token.getText());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousIndentAfterControlStatementVisitor();
  }

  private static class SuspiciousIndentAfterControlStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkWhitespaceSuspiciousness(statement, statement.getBody());
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkWhitespaceSuspiciousness(statement, statement.getBody());
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      checkWhitespaceSuspiciousness(statement, statement.getBody());
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkWhitespaceSuspiciousness(statement, statement.getBody());
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement elseStatement = statement.getElseBranch();
      if (elseStatement instanceof PsiBlockStatement || elseStatement instanceof PsiIfStatement) {
        return;
      }
      checkWhitespaceSuspiciousness(statement, (elseStatement == null) ? statement.getThenBranch() : elseStatement);
    }

    private void checkWhitespaceSuspiciousness(PsiStatement statement, PsiStatement body) {
      if (body instanceof PsiBlockStatement || body == null) {
        return;
      }
      final boolean lineBreakBeforeBody;
      PsiElement bodyWhiteSpace = body.getPrevSibling();
      if (!(bodyWhiteSpace instanceof PsiWhiteSpace)) {
        lineBreakBeforeBody = false;
        bodyWhiteSpace = statement.getPrevSibling();
        if (!(bodyWhiteSpace instanceof PsiWhiteSpace)) {
          return;
        }
      }
      else {
        final String text = bodyWhiteSpace.getText();
        final int bodyLineBreak = text.lastIndexOf('\n');
        if (bodyLineBreak < 0) {
          lineBreakBeforeBody = false;
          bodyWhiteSpace = statement.getPrevSibling();
          if (!(bodyWhiteSpace instanceof PsiWhiteSpace)) {
            return;
          }
        }
        else {
          lineBreakBeforeBody = true;
          final PsiElement statementWhiteSpace = statement.getPrevSibling();
          if (statementWhiteSpace instanceof PsiWhiteSpace) {
            final String siblingText = statementWhiteSpace.getText();
            final int statementLineBreak = siblingText.lastIndexOf('\n');
            if (statementLineBreak >= 0) {
              final int statementIndent = getIndent(siblingText.substring(statementLineBreak + 1));
              final String indentText = text.substring(bodyLineBreak + 1);
              final int bodyIndent = getIndent(indentText);
              if (statementIndent == bodyIndent) {
                registerErrorAtOffset(bodyWhiteSpace, bodyLineBreak + 1, indentText.length(), statement);
                return;
              }
            }
          }
        }
      }
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      if (nextStatement == null) {
        return;
      }
      final String text = bodyWhiteSpace.getText();
      final int bodyLineBreak = text.lastIndexOf('\n');
      if (bodyLineBreak < 0) {
        return;
      }
      final PsiElement nextWhiteSpace = nextStatement.getPrevSibling();
      if (!(nextWhiteSpace instanceof PsiWhiteSpace)) {
        return;
      }
      final String nextText = nextWhiteSpace.getText();
      final int nextLineBreak = nextText.lastIndexOf('\n');
      if (nextLineBreak < 0) {
        return;
      }
      final int bodyIndent = getIndent(text.substring(bodyLineBreak + 1));
      final String nextIndentText = nextText.substring(nextLineBreak + 1);
      final int nextIndent = getIndent(nextIndentText);
      if (nextIndent > bodyIndent || lineBreakBeforeBody && nextIndent == bodyIndent) {
        registerErrorAtOffset(nextWhiteSpace, nextLineBreak + 1, nextIndentText.length(), statement);
      }
    }

    private int getIndent(String indent) {
      int result = 0;
      for (int i = 0, length = indent.length(); i < length; i++) {
        final char c = indent.charAt(i);
        if (c == ' ') result++;
        else if (c == '\t') result += getTabSize();
        else if (c != '\f') throw new AssertionError(indent);
      }
      return result;
    }

    private int getTabSize() {
      return CodeStyle.getIndentOptions(getCurrentFile()).TAB_SIZE;
    }
  }
}
