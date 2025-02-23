// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import org.jetbrains.annotations.Contract

interface SearchWordQueryBuilder {

  /**
   * Orders to search word in files which contain [containerName] first.
   */
  @Contract(value = "_ -> new", pure = true)
  fun withContainerName(containerName: String?): SearchWordQueryBuilder

  /**
   * Sets case sensitivity.
   *
   * The query is case sensitive by default, so this method might be used to make the query case insensitive.
   */
  @Contract(value = "_ -> new", pure = true)
  fun caseSensitive(caseSensitive: Boolean): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in given contexts.
   *
   * @see com.intellij.psi.search.UsageSearchContext
   */
  @Contract(value = "_, _ -> new", pure = true)
  fun inContexts(context: SearchContext, vararg otherContexts: SearchContext): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in given contexts.
   *
   * @see com.intellij.psi.search.UsageSearchContext
   */
  @Contract(value = "_ -> new", pure = true)
  fun inContexts(contexts: Set<SearchContext>): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in given search scope.
   */
  @Contract(value = "_ -> new", pure = true)
  fun inScope(searchScope: SearchScope): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in files of given types.
   */
  @Contract(value = "_, _ -> new", pure = true)
  fun restrictFileTypes(fileType: FileType, vararg fileTypes: FileType): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in files of given language.
   *
   * For example `inFilesWithLanguage(JavaLanguage.INSTANCE)` will produce occurrences
   * from Java files and JSP files (since JSP contains code with JavaLanguage)
   *
   * This only checks the host file language.
   */
  @Contract(value = "_ -> new", pure = true)
  fun inFilesWithLanguage(language: Language): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in files of given language and its dialects.
   *
   * Same as [inFilesWithLanguage], except the language is matched with dialects.
   */
  @Contract(value = "_ -> new", pure = true)
  fun inFilesWithLanguageOfKind(language: Language): SearchWordQueryBuilder

  /**
   * Orders to include occurrences in language injections of any language as well as occurrences in host files.
   */
  @Contract(value = "-> new", pure = true)
  fun includeInjections(): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in language injections (instead of host files).
   */
  @Contract(value = "-> new", pure = true)
  fun inInjections(): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in language injections (instead of host files) of given language.
   */
  @Contract(value = "_ -> new", pure = true)
  fun inInjections(language: Language): SearchWordQueryBuilder

  /**
   * Orders to search occurrences in language injections (instead of host files) of given language and its dialects.
   */
  @Contract(value = "_ -> new", pure = true)
  fun inInjectionsOfKind(language: Language): SearchWordQueryBuilder

  /**
   * @param mapper pure function which is run once per each occurrence
   * @return query which returns results of applying the `mapper` to each occurrence
   */
  @Contract(value = "_ -> new", pure = true)
  fun <T : Any> buildQuery(mapper: LeafOccurrenceMapper<out T>): Query<out T>

  /**
   * @return query which generates occurrences by traversing the tree up starting from the bottom-most element with occurrence
   */
  @Contract(value = "-> new", pure = true)
  fun buildOccurrenceQuery(): Query<out TextOccurrence>

  /**
   * @return query which generates occurrences found in the bottom-most elements without traversing the tree up
   */
  @Contract(value = "-> new", pure = true)
  fun buildLeafOccurrenceQuery(): Query<out TextOccurrence>
}
