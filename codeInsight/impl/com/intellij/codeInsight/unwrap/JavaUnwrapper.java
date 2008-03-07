package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
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

  public TextRange collectTextRanges(PsiElement e, List<TextRange> toExtract) {
    try {
      Context c = new Context(false);
      doUnwrap(e, c);
      toExtract.addAll(c.myRangesToExtract);
      return e.getTextRange();
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
    private List<TextRange> myRangesToExtract = new ArrayList<TextRange>();
    private boolean myIsEffective;

    public Context(boolean isEffective) {
      myIsEffective = isEffective;
    }

    public void addElementToExtract(PsiElement e) {
      myRangesToExtract.add(e.getTextRange());
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
  }
}
