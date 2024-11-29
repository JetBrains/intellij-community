// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereViewItemsProvider<Item, Presentation: SearchEverywhereItemPresentation, Params> {

  fun processViewItems(searchParams: Params): Flow<SearchEverywhereViewItem<Item, Presentation>>

  fun isDumbAware(): Boolean = true
}