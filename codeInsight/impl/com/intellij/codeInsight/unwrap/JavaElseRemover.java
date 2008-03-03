package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaElseRemover extends JavaElseUnwrapperBase {
  public JavaElseRemover() {
    super(CodeInsightBundle.message("remove.else"));
  }

  @Override
  protected void unwrapElseBranch(PsiStatement branch, PsiElement parent) throws IncorrectOperationException {
    if (branch instanceof PsiIfStatement) {
      deleteSelectedElseIf((PsiIfStatement)branch);
    }
    else {
      branch.delete();
    }
  }

  private void deleteSelectedElseIf(PsiIfStatement selectedBranch) throws IncorrectOperationException {
    PsiIfStatement parentIf = (PsiIfStatement)selectedBranch.getParent();
    PsiStatement childElse = selectedBranch.getElseBranch();

    if (childElse == null) {
      selectedBranch.delete();
      return;
    }

    parentIf.setElseBranch(copyElement(childElse));
  }

  private PsiStatement copyElement(PsiStatement el) throws IncorrectOperationException {
    // we can not call el.copy() for 'else' since it sets context to parent 'if'. This cause copy to be invalidated
    // after parent 'if' removal in setElseBranch method.

    PsiManager manager = PsiManager.getInstance(el.getProject());
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    return factory.createStatementFromText(el.getText(), null);
  }
}
