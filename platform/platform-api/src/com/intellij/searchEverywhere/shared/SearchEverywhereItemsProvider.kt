// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.shared

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereItemsProvider {
  val id: SearchEverywhereProviderId
  fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData>
}
