// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.lang.Language
import com.intellij.lang.LanguageMatcher
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchWordQueryBuilder
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil.restrictScopeTo
import com.intellij.psi.search.PsiSearchScopeUtil.restrictScopeToFileLanguage
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import com.intellij.util.containers.toArray
import java.util.*

internal data class SearchWordQueryBuilderImpl(
  private val myProject: Project,
  private val myWord: String,
  private val myContainerName: String? = null,
  private val myCaseSensitive: Boolean = true,
  private val mySearchContexts: Set<SearchContext> = emptySet(),
  private val mySearchScope: SearchScope = GlobalSearchScope.EMPTY_SCOPE,
  private val myFileTypes: Collection<FileType>? = null,
  private val myFileLanguage: LanguageInfo = LanguageInfo.NoLanguage,
  private val myInjection: InjectionInfo = InjectionInfo.NoInjection
) : SearchWordQueryBuilder {

  override fun withContainerName(containerName: String?): SearchWordQueryBuilder = copy(myContainerName = containerName)

  override fun caseSensitive(caseSensitive: Boolean): SearchWordQueryBuilder = copy(myCaseSensitive = caseSensitive)

  override fun inScope(searchScope: SearchScope): SearchWordQueryBuilder = copy(mySearchScope = searchScope)

  override fun restrictFileTypes(fileType: FileType, vararg fileTypes: FileType): SearchWordQueryBuilder = copy(
    myFileTypes = listOf(fileType, *fileTypes)
  )

  override fun inFilesWithLanguage(language: Language): SearchWordQueryBuilder = copy(
    myFileLanguage = LanguageInfo.InLanguage(LanguageMatcher.match(language))
  )

  override fun inFilesWithLanguageOfKind(language: Language): SearchWordQueryBuilder = copy(
    myFileLanguage = LanguageInfo.InLanguage(LanguageMatcher.matchWithDialects(language))
  )

  override fun inContexts(context: SearchContext, vararg otherContexts: SearchContext): SearchWordQueryBuilder = copy(
    mySearchContexts = EnumSet.of(context, *otherContexts)
  )

  override fun inContexts(contexts: Set<SearchContext>): SearchWordQueryBuilder {
    require(contexts.isNotEmpty())
    return copy(mySearchContexts = contexts)
  }

  override fun includeInjections(): SearchWordQueryBuilder = copy(myInjection = InjectionInfo.IncludeInjections)

  override fun inInjections(): SearchWordQueryBuilder = copy(myInjection = InjectionInfo.InInjection(LanguageInfo.NoLanguage))

  override fun inInjections(language: Language): SearchWordQueryBuilder = copy(
    myInjection = InjectionInfo.InInjection(LanguageInfo.InLanguage(LanguageMatcher.match(language)))
  )

  override fun inInjectionsOfKind(language: Language): SearchWordQueryBuilder = copy(
    myInjection = InjectionInfo.InInjection(LanguageInfo.InLanguage(LanguageMatcher.matchWithDialects(language)))
  )

  private fun buildSearchScope(): SearchScope {
    var scope = mySearchScope
    if (myFileTypes != null) {
      scope = restrictScopeTo(scope, *myFileTypes.toArray(FileType.EMPTY_ARRAY))
    }
    if (myFileLanguage is LanguageInfo.InLanguage) {
      scope = restrictScopeToFileLanguage(myProject, scope, myFileLanguage.matcher)
    }
    return scope
  }

  override fun <T> buildQuery(mapper: LeafOccurrenceMapper<T>): Query<out T> = SearchWordQuery(
    Parameters(
      myProject,
      myWord,
      myContainerName,
      myCaseSensitive,
      mySearchContexts,
      buildSearchScope(),
      myInjection
    ),
    mapper
  )

  override fun buildOccurrenceQuery(): Query<out TextOccurrence> = buildQuery(TextOccurrenceWalker)

  override fun buildLeafOccurrenceQuery(): Query<out TextOccurrence> = buildQuery(IdLeafOccurenceMapper)

  internal data class Parameters(
    val project: Project,
    val word: String,
    val containerName: String?,
    val caseSensitive: Boolean,
    val searchContexts: Set<SearchContext>,
    val searchScope: SearchScope,
    val injection: InjectionInfo
  )
}
