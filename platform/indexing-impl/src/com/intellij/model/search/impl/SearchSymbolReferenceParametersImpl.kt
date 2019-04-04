// Copyright 2000-2019 JetBrains s.r.o.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchSymbolReferenceParameters.Builder
import com.intellij.model.search.impl.SearchSymbolReferenceParametersBase
import com.intellij.model.search.impl.SymbolReferenceQueryImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query

data class SearchSymbolReferenceParametersImpl(
  private val myProject: Project,
  private val myTarget: Symbol,
  private val myScope: SearchScope,
  private val myIgnoreUseScope: Boolean
) : SearchSymbolReferenceParametersBase(), Builder {

  constructor(project: Project, target: Symbol) : this(project, target, GlobalSearchScope.allScope(project), false)


  override fun getProject(): Project = myProject

  override fun getTarget(): Symbol = myTarget

  override fun getOriginalSearchScope(): SearchScope = myScope

  override fun isIgnoreUseScope(): Boolean = myIgnoreUseScope


  override fun inScope(scope: SearchScope): Builder = copy(myScope = scope)

  override fun ignoreUseScope(): Builder = ignoreUseScope(true)

  override fun ignoreUseScope(ignore: Boolean): Builder = copy(myIgnoreUseScope = ignore)

  override fun build(): Query<out SymbolReference> {
    return SymbolReferenceQueryImpl(this)
  }
}