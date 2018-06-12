// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * An occurrence processor for Find Usages functionality. A typical scenario involves invoking
 * {@link ReferencesSearch.SearchParameters#getOptimizer()} and passing this processor together with search string and some other parameters to
 * {@link SearchRequestCollector#searchWord(String, SearchScope, short, boolean, String, RequestResultProcessor)}.
 *
 * @author peter
 */
public abstract class RequestResultProcessor {
  private final Object myEquality;

  /**
   * @param equality this processor's equals/hashCode will delegate to this object
   */
  protected RequestResultProcessor(@NotNull Object... equality) {
    myEquality = Arrays.asList(equality);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RequestResultProcessor that = (RequestResultProcessor)o;

    return myEquality.equals(that.myEquality);
  }

  @Override
  public int hashCode() {
    return myEquality.hashCode();
  }

  /**
   * For every occurrence of the search string in the search scope, the infrastructure invokes this method for every PSI element having that
   * occurrence inside, from leaf elements up the tree until file element. The implementation is supposed to search for references
   * inside the given element at the given offset, and feed them to {@code consumer}.<p/>
   *
   * If you wish to process all offsets in the scope (e.g. file) at once, extend {@link BulkResultProcessor}.
   *
   * @return whether the consumer has returned false for any of the references (and thus stopped searching), false otherwise.
   */
  public abstract boolean processTextOccurrence(@NotNull PsiElement element, int offsetInElement, @NotNull Processor<? super PsiReference> consumer);

  /**
   * A variant of {@link RequestResultProcessor} that processes all text occurrences at once, e.g. for performance purposes.
   */
  @SuppressWarnings("unused")
  public static abstract class BulkResultProcessor extends RequestResultProcessor {

    public BulkResultProcessor() {
      super();
    }

    public BulkResultProcessor(@NotNull Object... equality) {
      super(equality);
    }

    @Override
    public boolean processTextOccurrence(@NotNull PsiElement element, int offsetInElement, @NotNull Processor<? super PsiReference> consumer) {
      return processTextOccurrences(element, new int[]{offsetInElement}, consumer);
    }

    /**
     * Invoked for every element of the search scope (e.g. file) with the array of all offsets of search string occurrences in this scope.
     * Offsets are relative to {@code scope} start offset. The implementation is supposed to search for references
     * inside the given element at the given offsets, and feed them to {@code consumer}.<p/>
     * @return whether the consumer has returned false for any of the references (and thus stopped searching), false otherwise.
     */
    public abstract boolean processTextOccurrences(@NotNull PsiElement scope, int[] offsetsInScope, @NotNull Processor<? super PsiReference> consumer);
  }
}
