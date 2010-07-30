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
    return "PsiSearchRequest: " + word + "; " + processor;
  }
}
