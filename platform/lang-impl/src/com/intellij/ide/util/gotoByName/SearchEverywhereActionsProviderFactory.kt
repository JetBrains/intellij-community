// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProviderFactory

class SearchEverywhereActionsProviderFactory: SearchEverywhereItemsProviderFactory {
  override fun getItemsProvider(): SearchEverywhereItemsProvider = SearchEverywhereActionsProvider()
}