// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.PsiSymbolReference
import com.intellij.model.SymbolReference
import com.intellij.model.search.*
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.LayeredQuery
import com.intellij.util.Query

@Suppress("Duplicates")
class ConstructorReferencesRequestor2 : SearchRequestor2 {

  override fun collectSearchRequests(parameters: SymbolReferenceSearchParameters): Collection<Query<out SymbolReference>> {
    val target = parameters.target as? PsiMethod ?: return emptyList()
    if (!target.isConstructor) return emptyList()
    val clazz = target.containingClass ?: return emptyList()

    val project = parameters.project
    val manager = PsiManager.getInstance(project)
    val scope = parameters.effectiveSearchScope
    val restrictedScope = (scope as? GlobalSearchScope)?.intersectWith(JavaFilesSearchScope(project)) ?: scope

    val classQuery = SymbolReferenceSearch.search(DefaultSymbolReferenceSearchParameters(
      parameters.project,
      clazz,
      restrictedScope,
      parameters.isIgnoreUseScope
    ))

    val service = SearchService.getInstance()

    // search usages like "new XXX(..)"
    val newXxxQuery = service.filter(classQuery, fun(classReference: SymbolReference): Boolean {
      if (classReference !is PsiSymbolReference) return false
      val parent = classReference.element.parent
      val newExpression = (if (parent is PsiAnonymousClass) parent.parent else parent) as? PsiNewExpression ?: return false
      val constructor = newExpression.resolveConstructor() ?: return false
      return manager.areElementsEquivalent(target, constructor)
    })

    // search usages like "XXX::new"
    val xxxNewQuery: Query<out SymbolReference> = service.map(classQuery, fun(classReference: SymbolReference): SymbolReference? {
      if (classReference is PsiSymbolReference) {
        val parent = classReference.element.parent
        if (parent is PsiMethodReferenceExpression // todo check PSI before resolving class target
            && parent.referenceNameElement is PsiKeyword
            && parent.isReferenceTo(target)) {
          return parent
        }
      }
      return null
    })

    // search usages like "this(..)"
    val thisQuery = service.searchWord(project, PsiKeyword.THIS).inScope(LocalSearchScope(clazz)).build(target)

    // search usages like "super(..)" in direct subclasses
    val inheritorsQuery = ClassInheritorsSearch.search(clazz, restrictedScope, false)
    val superQuery = LayeredQuery.mapping(inheritorsQuery) { inheritor ->
      service.searchWord(project, PsiKeyword.SUPER).inScope(LocalSearchScope(inheritor)).build(target)
    }

    return listOf(newXxxQuery, xxxNewQuery, thisQuery, superQuery)
  }
}
