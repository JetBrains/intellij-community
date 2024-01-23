// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

private class SearchEverywhereMlTabsCustomizationStrategy: TabsCustomizationStrategy {
  override fun getSeparateTabContributors(contributors: List<SearchEverywhereContributor<*>>): List<SearchEverywhereContributor<*>> {
    val separateTabContributors = contributors.filter { it.isShownInSeparateTab }
    return SearchEverywhereMlContributorReplacement.getFirstExtension()?.run {
      separateTabContributors.map { replaceInSeparateTab(it) }
    } ?: separateTabContributors
  }
}