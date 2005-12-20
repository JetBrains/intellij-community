package com.intellij.psi.search.searches;

import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.QueryFactory;
import com.intellij.util.Query;
import com.intellij.openapi.util.TextRange;

/**
 * @author yole
 */
public abstract class IndexPatternSearch extends QueryFactory<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  public static IndexPatternSearch INDEX_PATTERN_SEARCH_INSTANCE;

  public static class SearchParameters {
    private PsiFile myFile;
    private IndexPattern myPattern;
    private IndexPatternProvider myPatternProvider;
    private TextRange myRange;

    public SearchParameters(final PsiFile file, final IndexPattern pattern) {
      myFile = file;
      myPattern = pattern;
    }

    public SearchParameters(final PsiFile file, final IndexPatternProvider patternProvider) {
      myFile = file;
      myPatternProvider = patternProvider;
    }

    public PsiFile getFile() {
      return myFile;
    }

    public IndexPattern getPattern() {
      return myPattern;
    }

    public IndexPatternProvider getPatternProvider() {
      return myPatternProvider;
    }

    public TextRange getRange() {
      return myRange;
    }

    public void setRange(final TextRange range) {
      myRange = range;
    }
  }

  protected IndexPatternSearch() {
  }

  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPattern pattern) {
    final SearchParameters parameters = new SearchParameters(file, pattern);
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPattern pattern,
                                                     int startOffset, int endOffset) {
    final SearchParameters parameters = new SearchParameters(file, pattern);
    parameters.setRange(new TextRange(startOffset, endOffset));
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPatternProvider patternProvider) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider);
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }

  public static Query<IndexPatternOccurrence> search(PsiFile file, IndexPatternProvider patternProvider,
                                                     int startOffset, int endOffset) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider);
    parameters.setRange(new TextRange(startOffset, endOffset));
    return INDEX_PATTERN_SEARCH_INSTANCE.createQuery(parameters);
  }


  public static int getOccurrencesCount(PsiFile file, IndexPatternProvider provider) {
    return INDEX_PATTERN_SEARCH_INSTANCE.getOccurrencesCountImpl(file, provider);
  }

  public static int getOccurrencesCount(PsiFile file, IndexPattern pattern) {
    return INDEX_PATTERN_SEARCH_INSTANCE.getOccurrencesCountImpl(file, pattern);
  }

  protected abstract int getOccurrencesCountImpl(PsiFile file, IndexPatternProvider provider);
  protected abstract int getOccurrencesCountImpl(PsiFile file, IndexPattern pattern);
}
