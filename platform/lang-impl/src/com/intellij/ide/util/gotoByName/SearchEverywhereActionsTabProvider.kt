// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import com.intellij.platform.searchEverywhere.SearchEverywhereTabProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereActionsTabProvider: SearchEverywhereTabProvider {
  override fun getTab(): SearchEverywhereTab = SearchEverywhereActionsTab()
}