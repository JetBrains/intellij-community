// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter

open class AbstractPrimeSymbolNavigationContributor(val indexKey: StubIndexKey<String, NavigatablePsiElement>) : ChooseByNameContributorEx, DumbAware {
  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    StubIndex.getInstance().processAllKeys(indexKey, processor, scope, filter)
  }

  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    val scope = parameters.searchScope
    val filter = parameters.idFilter
    val project = parameters.project

    StubIndex.getInstance().processElements(indexKey, name, project, scope, filter, NavigatablePsiElement::class.java, processor)
  }
}