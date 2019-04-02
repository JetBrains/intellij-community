// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.PsiSymbolReference
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch

class ConstructorReferencesRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector) {
    val parameters = collector.parameters

    val target = parameters.target as? PsiMethod ?: return
    if (!target.isConstructor) return
    val clazz = target.containingClass ?: return

    val project = parameters.project
    val manager = PsiManager.getInstance(project)
    val scope = parameters.effectiveSearchScope
    val restrictedScope = (scope as? GlobalSearchScope)?.intersectWith(JavaFilesSearchScope(project)) ?: scope

    val classRequest = collector.searchTarget(clazz).inScope(restrictedScope)

    // search usages like "new XXX(..)"
    classRequest.searchFiltering(fun(classReference: SymbolReference): Boolean {
      if (classReference !is PsiSymbolReference) return false
      val parent = classReference.element.parent
      val newExpression = (if (parent is PsiAnonymousClass) parent.parent else parent) as? PsiNewExpression ?: return false
      val constructor = newExpression.resolveConstructor() ?: return false
      return manager.areElementsEquivalent(target, constructor)
    })

    // search usages like "XXX::new"
    classRequest.searchMapping(fun(classReference: SymbolReference): SymbolReference? {
      if (classReference is PsiSymbolReference) {
        val parent = classReference.element.parent
        if (parent is PsiMethodReferenceExpression
            && parent.referenceNameElement is PsiKeyword
            && parent.isReferenceTo(target)) {
          return parent
        }
      }
      return null
    })

    // search this() calls in current class
    collector.searchWord(PsiKeyword.THIS).inScope(LocalSearchScope(clazz)).search(target)

    val base = ClassInheritorsSearch.search(clazz, restrictedScope, false)

//    collector.sss(base) { inheritor ->
//      ClassInheritorsSearch.search(inheritor, restrictedScope, false)
//      collector.searchWord(PsiKeyword.SUPER).inScope(LocalSearchScope(inheritor)).search(target)
//      emptyList()
//    }
  }
}
