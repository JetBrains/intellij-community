package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.CodeInsightBundle;

public class JavaElseUnwrapper extends JavaUnwrapper {
  public JavaElseUnwrapper() {
    super(CodeInsightBundle.message("unwrap.else"));
  }

  protected boolean isAplicableToJava(PsiElement e) {
    return isElseBlock(e) || isElseKeyword(e);
  }

  private boolean isElseBlock(PsiElement el) {
    PsiElement p = el.getParent();
    return p instanceof PsiIfStatement && el == ((PsiIfStatement)p).getElseBranch();
  }

  private boolean isElseKeyword(PsiElement el) {
    PsiElement p = el.getParent();
    return p instanceof PsiIfStatement && el == ((PsiIfStatement)p).getElseElement();
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement elseBranch;

    if (isElseKeyword(element)) {
      elseBranch = ((PsiIfStatement)element.getParent()).getElseBranch();
      if (elseBranch == null) return;
    }
    else {
      elseBranch = (PsiStatement)element;
    }

    unwrapElseBranch(elseBranch, project);
  }

  private void unwrapElseBranch(PsiStatement branch, Project p) throws IncorrectOperationException {
    if (branch instanceof PsiIfStatement) {
      deleteSelectedElseIf((PsiIfStatement)branch, p);
    }
    else {
      branch.delete();
    }
  }

  private void deleteSelectedElseIf(PsiIfStatement selectedBranch, Project p) throws IncorrectOperationException {
    PsiIfStatement parentIf = (PsiIfStatement)selectedBranch.getParent();
    PsiStatement childElse = selectedBranch.getElseBranch();

    if (childElse == null) {
      selectedBranch.delete();
      return;
    }

    parentIf.setElseBranch(copyElement(childElse, p));
  }

  private PsiStatement copyElement(PsiStatement el, Project p) throws IncorrectOperationException {
    // we can not call el.copy() for 'else' since it sets context to parent 'if'. This cause copy to be invalidated
    // after parent 'if' removal in setElseBranch method.

    PsiManager manager = PsiManager.getInstance(p);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    return factory.createStatementFromText(el.getText(), null);
  }
}
