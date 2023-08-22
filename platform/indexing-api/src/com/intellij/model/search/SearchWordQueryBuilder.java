// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface SearchWordQueryBuilder {

  /**
   * Orders to search word in files which contain {@code containerName} first.
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder withContainerName(@Nullable String containerName);

  /**
   * Sets case sensitivity.<br/>
   * The query is case sensitive by default, so this method might be used to make the query case insensitive.
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder caseSensitive(boolean caseSensitive);

  /**
   * Orders to search occurrences in given contexts.
   *
   * @see com.intellij.psi.search.UsageSearchContext
   */
  @Contract(value = "_, _ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inContexts(@NotNull SearchContext context, SearchContext @NotNull ... otherContexts);

  /**
   * Orders to search occurrences in given contexts.
   *
   * @see com.intellij.psi.search.UsageSearchContext
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inContexts(@NotNull Set<SearchContext> contexts);

  /**
   * Orders to search occurrences in given search scope.
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inScope(@NotNull SearchScope searchScope);

  /**
   * Orders to search occurrences in files of given types.
   */
  @Contract(value = "_, _ -> new", pure = true)
  @NotNull SearchWordQueryBuilder restrictFileTypes(@NotNull FileType fileType, FileType @NotNull ... fileTypes);

  /**
   * Orders to search occurrences in files of given language.
   * <br/>
   * For example {@code inFilesWithLanguage(JavaLanguage.INSTANCE)} will produce occurrences
   * from Java files and JSP files (since JSP contains code with JavaLanguage)
   * <p>
   * This only checks the host file language.
   * </p>
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inFilesWithLanguage(@NotNull Language language);

  /**
   * Orders to search occurrences in files of given language and its dialects.
   * <p>
   * Same as {@link #inFilesWithLanguage}, except the language is matched with dialects.
   * </p>
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inFilesWithLanguageOfKind(@NotNull Language language);

  /**
   * Orders to include occurrences in language injections of any language as well as occurrences in host files.
   */
  @Contract(value = "-> new", pure = true)
  @NotNull SearchWordQueryBuilder includeInjections();

  /**
   * Orders to search occurrences in language injections (instead of host files).
   */
  @Contract(value = "-> new", pure = true)
  @NotNull SearchWordQueryBuilder inInjections();

  /**
   * Orders to search occurrences in language injections (instead of host files) of given language.
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inInjections(@NotNull Language language);

  /**
   * Orders to search occurrences in language injections (instead of host files) of given language and its dialects.
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull SearchWordQueryBuilder inInjectionsOfKind(@NotNull Language language);

  /**
   * @param mapper pure function which is run once per each occurrence
   * @return query which returns results of applying the {@code mapper} to each occurrence
   */
  @Contract(value = "_ -> new", pure = true)
  <T> @NotNull Query<? extends T> buildQuery(@NotNull LeafOccurrenceMapper<T> mapper);

  /**
   * @return query which generates occurrences by traversing the tree up starting from the bottom-most element with occurrence
   */
  @Contract(value = "-> new", pure = true)
  @NotNull Query<? extends TextOccurrence> buildOccurrenceQuery();

  /**
   * @return query which generates occurrences found in the bottom-most elements without traversing the tree up
   */
  @Contract(value = "-> new", pure = true)
  @NotNull Query<? extends TextOccurrence> buildLeafOccurrenceQuery();
}
