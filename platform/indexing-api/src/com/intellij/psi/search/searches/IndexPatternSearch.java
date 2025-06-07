// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to search for occurrences of specified regular expressions in comments.
 *
 * @see IndexPatternProvider
 * @see com.intellij.psi.search.PsiTodoSearchHelper#processFilesWithTodoItems(Processor)
 */
@Internal
public abstract class IndexPatternSearch extends ExtensibleQueryFactory<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<IndexPatternOccurrence, IndexPatternSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.indexPatternSearch");
  private static IndexPatternSearch ourInstance;

  protected IndexPatternSearch() {
    super(EP_NAME);
  }

  private static IndexPatternSearch getInstance() {
    IndexPatternSearch result = ourInstance;
    if (result == null) {
      result = ApplicationManager.getApplication().getService(IndexPatternSearch.class);
      ourInstance = result;
    }
    return result;
  }

  public static final class SearchParameters {
    private final PsiFile myFile;
    private final IndexPattern myPattern;
    private final IndexPatternProvider myPatternProvider;
    private final TextRange myRange;
    private final boolean myMultiLine;

    public SearchParameters(@NotNull PsiFile file, @NotNull IndexPattern pattern) {
      this(file, pattern,null);
    }
    public SearchParameters(@NotNull PsiFile file, @NotNull IndexPattern pattern, TextRange range) {
      myFile = file;
      myRange = range;
      myPatternProvider = null;
      myPattern = pattern;
      myMultiLine = false;
    }

    public SearchParameters(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider) {
      this(file, patternProvider,null, false);
    }
    public SearchParameters(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider, boolean multiLine) {
      this(file, patternProvider,null, multiLine);
    }
    public SearchParameters(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider, TextRange range) {
      this(file, patternProvider, range, false);
    }

    private SearchParameters(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider, TextRange range, boolean multiLine) {
      myFile = file;
      myPatternProvider = patternProvider;
      myRange = range;
      myPattern = null;
      myMultiLine = multiLine;
    }

    public @NotNull PsiFile getFile() {
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

    public boolean isMultiLine() {
      return myMultiLine;
    }
  }

  /**
   * Returns a query which can be used to process occurrences of the specified pattern
   * in the specified file. The query is executed by parsing the contents of the file.
   *
   * @param file    the file in which occurrences should be searched.
   * @param pattern the pattern to search for.
   * @return the query instance.
   */
  public static @NotNull Query<IndexPatternOccurrence> search(@NotNull PsiFile file, @NotNull IndexPattern pattern) {
    final SearchParameters parameters = new SearchParameters(file, pattern);
    return getInstance().createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of the specified pattern
   * in the specified text range. The query is executed by parsing the contents of the file.
   *
   * @param file        the file in which occurrences should be searched.
   * @param pattern     the pattern to search for.
   * @param startOffset the start offset of the range to search.
   * @param endOffset   the end offset of the range to search.
   * @return the query instance.
   */
  public static @NotNull Query<IndexPatternOccurrence> search(@NotNull PsiFile file,
                                                     @NotNull IndexPattern pattern,
                                                     int startOffset,
                                                     int endOffset) {
    final SearchParameters parameters = new SearchParameters(file, pattern, new TextRange(startOffset, endOffset));
    return getInstance().createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of any pattern from the specified provider in the specified text range.
   * The query is executed by parsing the contents of the file.
   */
  public static @NotNull Query<IndexPatternOccurrence> search(@NotNull PsiFile file,
                                                     @NotNull IndexPatternProvider patternProvider,
                                                     int startOffset,
                                                     int endOffset, boolean multiLines) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider, new TextRange(startOffset, endOffset), multiLines);
    return getInstance().createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of any pattern from the
   * specified provider in the specified file. The query is executed by parsing the
   * contents of the file.
   *
   * @param file            the file in which occurrences should be searched.
   * @param patternProvider the provider the patterns from which are searched.
   * @return the query instance.
   */
  public static @NotNull Query<IndexPatternOccurrence> search(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider);
    return getInstance().createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of any pattern from the
   * specified provider in the specified file. The query is executed by parsing the
   * contents of the file.
   *
   * @param file                 the file in which occurrences should be searched.
   * @param patternProvider      the provider the patterns from which are searched.
   * @param multiLineOccurrences whether continuation of occurrences on following lines should be detected
   *                             (will be returned as {@link IndexPatternOccurrence#getAdditionalTextRanges()}
   * @return the query instance.
   */
  public static @NotNull Query<IndexPatternOccurrence> search(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider,
                                                     boolean multiLineOccurrences) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider, multiLineOccurrences);
    return getInstance().createQuery(parameters);
  }

  /**
   * Returns a query which can be used to process occurrences of any pattern from the
   * specified provider in the specified text range. The query is executed by parsing the
   * contents of the file.
   *
   * @param file            the file in which occurrences should be searched.
   * @param patternProvider the provider the patterns from which are searched.
   * @param startOffset     the start offset of the range to search.
   * @param endOffset       the end offset of the range to search.
   * @return the query instance.
   */
  public static @NotNull Query<IndexPatternOccurrence> search(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider,
                                                     int startOffset, int endOffset) {
    final SearchParameters parameters = new SearchParameters(file, patternProvider, new TextRange(startOffset, endOffset));
    return getInstance().createQuery(parameters);
  }

  /**
   * Returns the number of occurrences of any pattern from the specified provider
   * in the specified file. The returned value is taken from the index, and the file
   * is not parsed.
   *
   * @param file            the file in which occurrences should be searched.
   * @param patternProvider the provider the patterns from which are searched.
   * @return the number of pattern occurrences.
   */
  public static int getOccurrencesCount(@NotNull PsiFile file, @NotNull IndexPatternProvider patternProvider) {
    return getInstance().getOccurrencesCountImpl(file, patternProvider);
  }

  /**
   * Returns the number of occurrences of the specified pattern
   * in the specified file. The returned value is taken from the index, and the file
   * is not parsed.
   *
   * @param file            the file in which occurrences should be searched.
   * @param pattern     the pattern to search for.
   * @return the number of pattern occurrences.
   */
  public static int getOccurrencesCount(@NotNull PsiFile file, @NotNull IndexPattern pattern) {
    return getInstance().getOccurrencesCountImpl(file, pattern);
  }

  protected abstract int getOccurrencesCountImpl(@NotNull PsiFile file, @NotNull IndexPatternProvider provider);
  protected abstract int getOccurrencesCountImpl(@NotNull PsiFile file, @NotNull IndexPattern pattern);
}
