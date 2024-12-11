// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereTabHelper
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereActionsTab(project: Project, sessionId: Int): SearchEverywhereTab {
  private val helper = SearchEverywhereTabHelper(project,
                                                 sessionId,
                                                 listOf(SearchEverywhereProviderId("com.intellij.ActionsItemsProvider")))

  override val name: String
    get() = LangBundle.message("tab.title.actions")

  override val shortName: String
    get() = name

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> =
    helper.getItems(params)
}

