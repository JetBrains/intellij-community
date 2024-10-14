// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference

@ApiStatus.Internal
interface SearchEverywhereMlContributorReplacement {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereMlContributorReplacement> = ExtensionPointName.create(
      "com.intellij.searchEverywhereMlContributorReplacement")

    var initEvent = WeakReference<AnActionEvent>(null)
      private set

    @JvmStatic
    fun getFirstExtension(): SearchEverywhereMlContributorReplacement? {
      val extensions = EP_NAME.extensionList
      return extensions.firstOrNull()
    }

    @JvmStatic
    fun saveInitEvent(event: AnActionEvent) {
      initEvent = WeakReference(event)
    }
  }

  fun replaceInSeparateTab(contributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*>
}