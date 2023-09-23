// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereMlContributorReplacementService {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereMlContributorReplacementService> = ExtensionPointName.create(
      "com.intellij.searchEverywhereMlContributorReplacementService")

    var initEvent: AnActionEvent? = null
      private set

    @JvmStatic
    fun getInstance(): SearchEverywhereMlContributorReplacementService? {
      return EP_NAME.extensions.firstOrNull()
    }

    @JvmStatic
    fun saveInitEvent(event: AnActionEvent) {
      initEvent = event
    }
  }

  fun replaceInSeparateTab(contributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*>
}