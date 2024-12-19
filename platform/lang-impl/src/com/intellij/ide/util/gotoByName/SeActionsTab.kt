// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeTabHelper
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeActionsTab private constructor(private val helper: SeTabHelper): SeTab {
  override val name: String
    get() = LangBundle.message("tab.title.actions")

  override val shortName: String
    get() = name

  override fun getItems(params: SeParams): Flow<SeItemData> = helper.getItems(params)

  companion object {
    suspend fun create(project: Project, sessionRef: DurableRef<SeSessionEntity>): SeActionsTab {
      val helper = SeTabHelper.create(project,
                                      sessionRef,
                                      listOf(SeProviderId("com.intellij.ActionsItemsProvider")),
                                      false)
      return SeActionsTab(helper)
    }
  }
}

