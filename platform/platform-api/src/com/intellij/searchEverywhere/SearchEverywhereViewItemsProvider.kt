// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere

import kotlinx.coroutines.CoroutineScope

interface SearchEverywhereViewItemsProvider<Item, Presentation: SearchEverywhereItemPresentation, Params> {

  suspend fun processViewItems(scope: CoroutineScope, searchParams: Params, processor: (SearchEverywhereListItem<Item, Presentation>) -> Boolean)

  fun isDumbAware(): Boolean = true
}