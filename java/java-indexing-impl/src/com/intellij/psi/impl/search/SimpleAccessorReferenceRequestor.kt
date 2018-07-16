// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PropertyUtilBase

class SimpleAccessorReferenceRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector) {
    if (!Registry.`is`("ide.symbol.reference.search")) return
    val parameters = collector.parameters
    val project = parameters.project
    val method = parameters.target as? PsiMethod ?: return
    val propertyName = PropertyUtilBase.getPropertyName(method) ?: return
    if (propertyName.isEmpty()) return

    val additional = CustomPropertyScopeProvider.getPropertyScope(project)
    val propScope = parameters.effectiveSearchScope.intersectWith(method.useScope).intersectWith(additional)
    collector.searchWord(propertyName).inScope(propScope).setSearchContext(UsageSearchContext.IN_FOREIGN_LANGUAGES).search(method)
  }
}
