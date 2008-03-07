package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.TextRange;

import java.util.List;

public class JavaElseUnwrapper extends JavaElseUnwrapperBase {
  public JavaElseUnwrapper() {
    super(CodeInsightBundle.message("unwrap.else"));
  }

  @Override
  public TextRange collectTextRanges(PsiElement e, List<TextRange> toExtract) {
    super.collectTextRanges(e, toExtract);
    return findTopmostIfStatement(e).getTextRange();
  }

  @Override
  protected void unwrapElseBranch(PsiStatement branch, PsiElement parent, Context context) throws IncorrectOperationException {
    // if we have 'else if' then we have to extract statements from the 'if' branch
    if (branch instanceof PsiIfStatement) {
      branch = ((PsiIfStatement)branch).getThenBranch();
    }

    parent = findTopmostIfStatement(parent);

    context.extractFromBlockOrSingleStatement(branch, parent);
    context.delete(parent);
  }

  private PsiElement findTopmostIfStatement(PsiElement parent) {
    while (parent.getParent() instanceof PsiIfStatement) {
      parent = parent.getParent();
    }
    return parent;
  }
}