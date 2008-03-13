package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class JavaElseRemover extends JavaElseUnwrapperBase {
  public JavaElseRemover() {
    super(CodeInsightBundle.message("remove.else"));
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return ((PsiIfStatement)e.getParent()).getElseBranch();
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

    context.setElseBranch(parentIf, childElse);
  }
}
