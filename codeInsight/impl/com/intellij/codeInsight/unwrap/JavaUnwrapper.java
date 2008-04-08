package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class JavaUnwrapper implements Unwrapper {
  private String myDescription;

  public JavaUnwrapper(String description) {
    myDescription = description;
  }

  public abstract boolean isApplicableTo(PsiElement e);

  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
  }

  public String getDescription(PsiElement e) {
    return myDescription;
  }

  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    try {
      Context c = new Context(false);
      doUnwrap(e, c);
      toExtract.addAll(c.myElementsToExtract);
      return e;
    }
    catch (IncorrectOperationException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    doUnwrap(element, new Context(true));
  }

  protected abstract void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException;

  protected boolean isElseBlock(PsiElement e) {
    PsiElement p = e.getParent();
    return p instanceof PsiIfStatement && e == ((PsiIfStatement)p).getElseBranch();
  }

  protected static class Context {
    private List<PsiElement> myElementsToExtract = new ArrayList<PsiElement>();
    private boolean myIsEffective;

    public Context(boolean isEffective) {
      myIsEffective = isEffective;
    }

    public void addElementToExtract(PsiElement e) {
      myElementsToExtract.add(e);
    }

    public void extractFromBlockOrSingleStatement(PsiStatement block, PsiElement from) throws IncorrectOperationException {
      if (block instanceof PsiBlockStatement) {
        extractFromCodeBlock(((PsiBlockStatement)block).getCodeBlock(), from);
      }
      else if (block != null && !(block instanceof PsiEmptyStatement)) {
        extract(block, block, from);
      }
    }

    public void extractFromCodeBlock(PsiCodeBlock block, PsiElement from) throws IncorrectOperationException {
      if (block == null) return;
      extract(block.getFirstBodyElement(), block.getLastBodyElement(), from);
    }

    private void extract(PsiElement first, PsiElement last, PsiElement from) throws IncorrectOperationException {
      if (first == null) return;

      // trim leading empty spaces
      while (first != last && first instanceof PsiWhiteSpace) {
        first = first.getNextSibling();
      }

      // trim trailing empty spaces
      while (last != first && last instanceof PsiWhiteSpace) {
        last = last.getPrevSibling();
      }

      // nothing to extract
      if (first == last && last instanceof PsiWhiteSpace) return;

      if (myIsEffective) from.getParent().addRangeBefore(first, last, from);

      do {
        addElementToExtract(first);
        first = first.getNextSibling();
      }
      while (first != null && first.getPrevSibling() != last);
    }

    public void delete(PsiElement e) throws IncorrectOperationException {
      if (myIsEffective) e.delete();
    }
    
    public void deleteExactly(PsiElement e) throws IncorrectOperationException {
      if (myIsEffective) {
        // have to use 'parent.deleteChildRange' since 'e.delete' is too smart:
        // it attempts to remove not only the element but sometimes whole expression.
        e.getParent().deleteChildRange(e, e);
      }
    }

    public void setElseBranch(PsiIfStatement ifStatement, PsiStatement elseBranch) throws IncorrectOperationException {
      if (myIsEffective) ifStatement.setElseBranch(copyElement(elseBranch));
      addElementToExtract(elseBranch);
    }

    private PsiStatement copyElement(PsiStatement e) throws IncorrectOperationException {
      // We can not call el.copy() for 'else' since it sets context to parent 'if'.
      // This causes copy to be invalidated after parent 'if' is removed by setElseBranch method.

      PsiManager manager = PsiManager.getInstance(e.getProject());
      PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      return factory.createStatementFromText(e.getText(), null);
    }
  }
}
