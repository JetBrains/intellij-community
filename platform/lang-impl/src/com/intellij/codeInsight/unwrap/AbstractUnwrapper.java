// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class AbstractUnwrapper<C extends AbstractUnwrapper.AbstractContext> implements Unwrapper {
  private final @NotNull @Nls String myDescription;

  public AbstractUnwrapper(@NotNull @Nls String description) {
    myDescription = description;
  }

  @Override
  public abstract boolean isApplicableTo(@NotNull PsiElement e);

  @Override
  public void collectElementsToIgnore(@NotNull PsiElement element, @NotNull Set<PsiElement> result) {
  }

  @NotNull
  @Override
  public @Nls String getDescription(@NotNull PsiElement e) {
    return myDescription;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
    try {
      C c = createContext();
      doUnwrap(e, c);
      toExtract.addAll(c.myElementsToExtract);
      return e;
    }
    catch (IncorrectOperationException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NotNull
  @Override
  public List<PsiElement> unwrap(@NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    C c = createContext();
    c.myIsEffective = true;
    doUnwrap(element, c);
    return c.myElementsToExtract;
  }

  protected abstract void doUnwrap(PsiElement element, C context) throws IncorrectOperationException;

  protected abstract C createContext();

  public abstract static class AbstractContext {
    protected final List<PsiElement> myElementsToExtract = new ArrayList<>();
    protected boolean myIsEffective;

    public void addElementToExtract(PsiElement e) {
      myElementsToExtract.add(e);
    }

    public void extractElement(PsiElement element, PsiElement from) throws IncorrectOperationException {
      extract(element, element, from);
    }

    protected abstract boolean isWhiteSpace(PsiElement element);

    protected void extract(PsiElement first, PsiElement last, PsiElement from) throws IncorrectOperationException {
      // trim leading empty spaces
      while (first != last && isWhiteSpace(first)) {
        first = first.getNextSibling();
      }

      // trim trailing empty spaces
      while (last != first && isWhiteSpace(last)) {
        last = last.getPrevSibling();
      }

      // nothing to extract
      if (first == null || last == null || first == last && isWhiteSpace(last)) return;

      PsiElement toExtract = first;
      if (myIsEffective) {
        toExtract = addRangeBefore(first, last, from.getParent(), from);
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

    /**
     * Adds range [first, last] before anchor under parent.
     *
     * @return the first child element which was actually added
     */
    protected PsiElement addRangeBefore(@NotNull PsiElement first,
                                        @NotNull PsiElement last,
                                        @NotNull PsiElement parent,
                                        @NotNull PsiElement anchor) throws IncorrectOperationException {
      return parent.addRangeBefore(first, last, anchor);
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

    public final boolean isEffective() {
      return myIsEffective;
    }
  }
}
