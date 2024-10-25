// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere

import kotlinx.coroutines.CoroutineScope

interface SearchEverywhereItemsProvider<I, P> {

  suspend fun processItems(scope: CoroutineScope, searchParams: P, processor: (I, Int) -> Boolean)
}