package com.intellij.psi.search;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiSearchRequest {
  public final SearchScope searchScope;
  public final String word;
  public final short searchContext;
  public final boolean caseSensitive;
  public final RequestResultProcessor processor;

  public PsiSearchRequest(@NotNull SearchScope searchScope,
                       @NotNull String word,
                       short searchContext,
                       boolean caseSensitive,
                       @NotNull RequestResultProcessor processor) {

    this.searchScope = searchScope;
    this.word = word;
    this.searchContext = searchContext;
    this.caseSensitive = caseSensitive;
    this.processor = processor;
  }

  @Override
  public String toString() {
    return word + " -> " + processor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiSearchRequest)) return false;

    PsiSearchRequest that = (PsiSearchRequest)o;

    if (caseSensitive != that.caseSensitive) return false;
    if (searchContext != that.searchContext) return false;
    if (processor != null ? !processor.equals(that.processor) : that.processor != null) return false;
    if (searchScope != null ? !searchScope.equals(that.searchScope) : that.searchScope != null) return false;
    if (word != null ? !word.equals(that.word) : that.word != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = searchScope != null ? searchScope.hashCode() : 0;
    result = 31 * result + (word != null ? word.hashCode() : 0);
    result = 31 * result + (int)searchContext;
    result = 31 * result + (caseSensitive ? 1 : 0);
    result = 31 * result + (processor != null ? processor.hashCode() : 0);
    return result;
  }
}
