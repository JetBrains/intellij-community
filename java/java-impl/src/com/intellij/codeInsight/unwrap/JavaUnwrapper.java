/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class JavaUnwrapper implements Unwrapper {
  private final String myDescription;

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

  public List<PsiElement> unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    Context c = new Context(true);
    doUnwrap(element, c);
    return c.myElementsToExtract;
  }

  protected abstract void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException;

  protected static class Context {
    private final List<PsiElement> myElementsToExtract = new ArrayList<PsiElement>();
    private final boolean myIsEffective;

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

    public void extractElement(PsiElement element, PsiElement from) throws IncorrectOperationException {
      extract(element, element, from);
    }

    private void extract(PsiElement first, PsiElement last, PsiElement from) throws IncorrectOperationException {
      // trim leading empty spaces
      while (first != last && first instanceof PsiWhiteSpace) {
        first = first.getNextSibling();
      }

      // trim trailing empty spaces
      while (last != first && last instanceof PsiWhiteSpace) {
        last = last.getPrevSibling();
      }

      // nothing to extract
      if (first == null || last == null || first == last && last instanceof PsiWhiteSpace) return;

      PsiElement toExtract = first;
      if (myIsEffective) {
        toExtract = from.getParent().addRangeBefore(first, last, from);
      }

      do {
        if (toExtract != null) {
          addElementToExtract(toExtract);
          toExtract = toExtract.getNextSibling();
        }
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
      PsiStatement toExtract = elseBranch;
      if (myIsEffective) {
        ifStatement.setElseBranch(copyElement(elseBranch));
        toExtract = ifStatement.getElseBranch();
      }
      addElementToExtract(toExtract);
    }

    private static PsiStatement copyElement(PsiStatement e) throws IncorrectOperationException {
      // We cannot call el.copy() for 'else' since it sets context to parent 'if'.
      // This causes copy to be invalidated after parent 'if' is removed by setElseBranch method.
      PsiElementFactory factory = JavaPsiFacade.getInstance(e.getProject()).getElementFactory();
      return factory.createStatementFromText(e.getText(), null);
    }
  }
}
