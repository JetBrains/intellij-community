package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class UnwrapHandler implements CodeInsightActionHandler {
  private Project myProject;

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    myProject = project;
    try {
      PsiElement el = getSelectedElement(editor, file);
      while (el != null && !dispatch(el)) {
        el = el.getParent();
      }
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private PsiElement getSelectedElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    return file.findElementAt(offset);
  }

  private boolean dispatch(PsiElement el) throws IncorrectOperationException {
    if (isElseBlock(el)) {
      unwrapElse((PsiStatement)el);
    }
    else if (isElseKeyword(el)) {
      PsiStatement elseBranch = ((PsiIfStatement)el.getParent()).getElseBranch();
      if (elseBranch != null) unwrapElse(elseBranch);
    }
    else if (el instanceof PsiIfStatement) {
      uwrapIf((PsiIfStatement)el);
    }
    else if (el instanceof PsiTryStatement) {
      unwrapTry((PsiTryStatement)el);
    }
    else if (el instanceof PsiCatchSection) {
      unwrapCatch((PsiCatchSection)el);
    }
    else {
      return false;
    }
    return true;
  }

  private boolean isElseBlock(PsiElement el) {
    PsiElement p = el.getParent();
    return p instanceof PsiIfStatement && el == ((PsiIfStatement)p).getElseBranch();
  }

  private boolean isElseKeyword(PsiElement el) {
    PsiElement p = el.getParent();
    return p instanceof PsiIfStatement && el == ((PsiIfStatement)p).getElseElement();
  }

  private void uwrapIf(PsiIfStatement el) throws IncorrectOperationException {
    PsiStatement then = el.getThenBranch();

    if (then instanceof PsiBlockStatement) {
      extractFromCodeBlock(((PsiBlockStatement)then).getCodeBlock(), el);
    }
    else if (then != null && !(then instanceof PsiEmptyStatement)) {
      extract(new PsiElement[]{then}, el);
    }

    el.delete();
  }

  private void unwrapElse(PsiStatement el) throws IncorrectOperationException {
    if (el instanceof PsiIfStatement) {
      deleteSelectedElseIf((PsiIfStatement)el);
    }
    else {
      el.delete();
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

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    return factory.createStatementFromText(el.getText(), null);
  }

  private void unwrapTry(PsiTryStatement el) throws IncorrectOperationException {
    extractFromCodeBlock(el.getTryBlock(), el);
    extractFromCodeBlock(el.getFinallyBlock(), el);

    el.delete();
  }

  private void unwrapCatch(PsiCatchSection el) throws IncorrectOperationException {
    PsiTryStatement tryEl = (PsiTryStatement)el.getParent();
    if (tryEl.getCatchBlocks().length > 1) {
      el.delete();
    } else {
      unwrapTry(tryEl);
    }
  }

  private void extractFromCodeBlock(PsiCodeBlock block, PsiElement from) throws IncorrectOperationException {
    if (block == null) return;
    extract(block.getStatements(), from);
  }

  private void extract(PsiElement[] elements, PsiElement from) throws IncorrectOperationException {
    if (elements.length == 0) return;

    PsiElement first = elements[0];
    PsiElement last = elements[elements.length - 1];
    from.getParent().addRangeBefore(first, last, from);
  }
}
