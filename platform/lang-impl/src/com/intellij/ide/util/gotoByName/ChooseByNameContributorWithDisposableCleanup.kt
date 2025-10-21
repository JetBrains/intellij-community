// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.navigation.ChooseByNameContributorEx2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter

interface ChooseByNameContributorWithDisposableCleanup : ChooseByNameContributorEx2 {

  fun processNamesWithDisposable(disposable: Disposable, processor: Processor<in String>, parameters: FindSymbolParameters)
  fun processElementsWithNameWithDisposable(disposable: Disposable, name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters)

  @Deprecated("Please use the Disposable-enabled version, this one cleans up too early. Still correct, but takes more computation")
  override fun processNames(processor: Processor<in String>, parameters: FindSymbolParameters) {
    val localDisposable = Disposer.newDisposable()
    processNamesWithDisposable(localDisposable, processor, parameters)
    Disposer.dispose(localDisposable)
  }

  @Deprecated("Please use the Disposable-enabled version, this one cleans up too early. Still correct, but takes more computation")
  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    val localDisposable = Disposer.newDisposable()
    processElementsWithNameWithDisposable(localDisposable, name, processor, parameters)
    Disposer.dispose(localDisposable)
  }

  @Deprecated("Please use the Disposable-enabled version, this one DOES NOTHING")
  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    throw UnsupportedOperationException("Use RdChooseByNameContributor.processNamesLifetimed")
  }
}