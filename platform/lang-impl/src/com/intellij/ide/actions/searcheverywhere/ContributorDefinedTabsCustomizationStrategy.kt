// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

class ContributorDefinedTabsCustomizationStrategy: TabsCustomizationStrategy {

  override fun getSeparateTabContributors(contributors: List<SearchEverywhereContributor<*>>): List<SearchEverywhereContributor<*>> =
    contributors.filter { it.isShownInSeparateTab }

}