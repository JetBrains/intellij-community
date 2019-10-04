// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a loop which traverses an iterable (e.g. via iterator or for-each loop statement).
 *
 * @see IteratorDeclaration
 * @see ForEachCollectionTraversal
 */
public abstract class IterableTraversal {
  protected final @Nullable PsiExpression myIterable;
  protected final boolean myCollection;

  IterableTraversal(@Nullable PsiExpression iterable, boolean collection) {
    myIterable = iterable;
    myCollection = collection;
  }

  /**
   * @return an expression which represent an iterable
   */
  @Nullable
  public final PsiExpression getIterable() {
    return myIterable;
  }

  /**
   * @return true if iterable is known to be a collection
   */
  public final boolean isCollection() {
    return myCollection;
  }

  /**
   * @param candidate element to check
   * @return true if given element is a method call which removes current element from iterable
   */
  public abstract boolean isRemoveCall(PsiElement candidate);
}
