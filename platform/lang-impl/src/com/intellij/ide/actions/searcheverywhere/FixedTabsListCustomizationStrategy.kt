// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

@Deprecated("The old Search Everywhere is being sunset in favor of the new (Split) Search Everywhere (com.intellij.platform.searchEverywhere).")
abstract class FixedTabsListCustomizationStrategy(private val shownIds: List<String>): TabsCustomizationStrategy {

  override fun getSeparateTabContributors(contributors: List<SearchEverywhereContributor<*>>)
  : List<SearchEverywhereContributor<*>> =
    contributors.filter { it.searchProviderId in shownIds && it.isShownInSeparateTab}
}