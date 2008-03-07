package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.TextRange;

import java.util.List;

public class JavaElseRemover extends JavaElseUnwrapperBase {
  public JavaElseRemover() {
    super(CodeInsightBundle.message("remove.else"));
  }

  @Override
  public TextRange collectTextRanges(PsiElement e, List<TextRange> toExtract) {
    super.collectTextRanges(e, toExtract);
    return ((PsiIfStatement)e.getParent()).getElseBranch().getTextRange();
  }

  @Override
  protected void unwrapElseBranch(PsiStatement branch, PsiElement parent, Context context) throws IncorrectOperationException {
    if (branch instanceof PsiIfStatement) {
      deleteSelectedElseIf((PsiIfStatement)branch, context);
    }
    else {
      context.delete(branch);
    }
  }

  private void deleteSelectedElseIf(PsiIfStatement selectedBranch, Context context) throws IncorrectOperationException {
    PsiIfStatement parentIf = (PsiIfStatement)selectedBranch.getParent();
    PsiStatement childElse = selectedBranch.getElseBranch();

    if (childElse == null) {
      context.delete(selectedBranch);
      return;
    }

    context.addElementToExtract(childElse);
    parentIf.setElseBranch(copyElement(childElse));
  }

  private PsiStatement copyElement(PsiStatement e) throws IncorrectOperationException {
    // we can not call el.copy() for 'else' since it sets context to parent 'if'. This cause copy to be invalidated
    // after parent 'if' removal in setElseBranch method.

    PsiManager manager = PsiManager.getInstance(e.getProject());
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    return factory.createStatementFromText(e.getText(), null);
  }
}
