// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters

abstract class DisposableGotoModelWithPersistentFilter<T>(
  project: Project, chooseByNameContributors: List<ChooseByNameContributor>,
) : FilteringGotoByModel<T>(project, chooseByNameContributors), Disposable.Default {

  override fun doProcessContributorNames(contributor: ChooseByNameContributor?, parameters: FindSymbolParameters, processor: Processor<in String>) {
    if (contributor is ChooseByNameContributorWithDisposableCleanup) {
      contributor.processNamesWithDisposable(this, processor, parameters)
    }
    else {
      super.doProcessContributorNames(contributor, parameters, processor)
    }
  }

  override fun doProcessContributorForName(contributor: ChooseByNameContributor, name: String, parameters: FindSymbolParameters, canceled: ProgressIndicator, items: MutableList<in NavigationItem>, count: IntArray, searchInLibraries: Boolean) {
    if (contributor is ChooseByNameContributorWithDisposableCleanup) {
      contributor.processElementsWithNameWithDisposable(this, name, { item ->
        canceled.checkCanceled()
        count[0]++
        if (acceptItem(item)) items.add(item)
        true
      }, parameters)
    }
    else {
      super.doProcessContributorForName(contributor, name, parameters, canceled, items, count, searchInLibraries)
    }
  }
}