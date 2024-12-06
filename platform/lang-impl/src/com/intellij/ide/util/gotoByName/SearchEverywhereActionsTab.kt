// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.lang.LangBundle
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereActionsTab: SearchEverywhereTab {
  override val name: String
    get() = LangBundle.message("tab.title.actions")
  override val shortName: String
    get() = name
  override val providers: Collection<String>
    get() = listOf("com.intellij.ActionsItemsProvider")
}

