package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class UnwrapHandler implements CodeInsightActionHandler {
  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement el = file.findElementAt(offset);

    try {
      while(el != null && !dispatch(el)) {
        el = el.getParent();
      }
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean dispatch(PsiElement el) throws IncorrectOperationException {
    if (el instanceof PsiIfStatement) {
      uwrapIfStatement((PsiIfStatement)el);
      return true;
    }

    return false;
  }

  private void uwrapIfStatement(PsiIfStatement el) throws IncorrectOperationException {
    extractStatements(el.getThenBranch(), el);
    extractStatements(el.getElseBranch(), el);

    el.delete();
  }

  private void extractStatements(PsiStatement from, PsiStatement to) throws IncorrectOperationException {
    if (from == null) return;

    PsiStatement[] statements = PsiStatement.EMPTY_ARRAY;

    if (from instanceof PsiBlockStatement) {
      statements = ((PsiBlockStatement)from).getCodeBlock().getStatements();
    } else if (!(from instanceof PsiEmptyStatement)) {
      statements = new PsiStatement[] { from };
    }

    if (statements.length == 0) return;

    to.getParent().addRangeBefore(statements[0], statements[statements.length - 1], to);
  }
}
