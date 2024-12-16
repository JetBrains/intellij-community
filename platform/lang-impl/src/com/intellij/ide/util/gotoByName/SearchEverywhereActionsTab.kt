// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereTabHelper
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereActionsTab(project: Project, sessionRef: DurableRef<SearchEverywhereSessionEntity>): SearchEverywhereTab {
  private val helper = SearchEverywhereTabHelper(project,
                                                 sessionRef,
                                                 listOf(SearchEverywhereProviderId("com.intellij.ActionsItemsProvider")))

  override val name: String
    get() = LangBundle.message("tab.title.actions")

  override val shortName: String
    get() = name

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> = helper.getItems(params)
}

