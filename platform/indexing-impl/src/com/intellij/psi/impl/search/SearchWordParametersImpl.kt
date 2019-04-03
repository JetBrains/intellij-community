// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.Symbol
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchContext.*
import com.intellij.model.search.SearchWordParameters
import com.intellij.model.search.SearchWordParameters.Builder
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import com.intellij.util.containers.toArray
import java.util.*

internal data class SearchWordParametersImpl(
  private val project: Project,
  private val myWord: String,
  private val mySearchScope: SearchScope,
  private val myFileTypes: Collection<FileType>?,
  private val myCaseInsensitive: Boolean,
  private val mySearchContexts: Set<SearchContext>?,
  private val myTargetHint: Symbol?
) : SearchWordParameters, Builder {

  constructor(project: Project, word: String) : this(project, word, GlobalSearchScope.EMPTY_SCOPE, null, true, null, null)

  override fun getProject(): Project = project

  override fun getWord(): String = myWord

  override fun getSearchScope(): SearchScope {
    val scope = mySearchScope
    return if (myFileTypes != null && myFileTypes.isNotEmpty()) {
      PsiSearchScopeUtil.restrictScopeTo(scope, *myFileTypes.toArray(FileType.EMPTY_ARRAY))
    }
    else {
      scope
    }
  }

  override fun isCaseSensitive(): Boolean = myCaseInsensitive

  override fun getSearchContexts(): Set<SearchContext> {
    if (mySearchContexts != null) {
      return mySearchContexts
    }
    else {
      val result = EnumSet.of<SearchContext>(IN_CODE, IN_FOREIGN_LANGUAGES, IN_COMMENTS)
      if (myTargetHint is PsiFileSystemItem) {
        result.add(IN_STRINGS)
      }
      return result
    }
  }

  override fun getTargetHint(): Symbol? = myTargetHint

  override fun inScope(searchScope: SearchScope): Builder {
    return copy(mySearchScope = searchScope)
  }

  override fun restrictScopeTo(vararg fileTypes: FileType): Builder {
    return copy(myFileTypes = listOf(*fileTypes))
  }

  override fun caseInsensitive(): Builder {
    return copy(myCaseInsensitive = true)
  }

  override fun inContexts(context: SearchContext, vararg otherContexts: SearchContext): Builder {
    return copy(mySearchContexts = EnumSet.of(context, *otherContexts))
  }

  override fun inAllContexts(): Builder {
    return copy(mySearchContexts = EnumSet.allOf(SearchContext::class.java))
  }

  override fun withTargetHint(target: Symbol): Builder {
    return copy(myTargetHint = target)
  }

  override fun build(): Query<out TextOccurrence> {
    return SearchWordQueryImpl(project, this)
  }
}
