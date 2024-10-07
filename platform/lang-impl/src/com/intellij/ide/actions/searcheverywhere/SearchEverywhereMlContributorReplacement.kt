// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
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

  @ApiStatus.Internal
  fun configureContributor(newContributor: SearchEverywhereContributor<*>,
                           parentContributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*> {
    // Make sure replacing contributor is disposed when [SearchEverywhereUI] is disposed
    // We achieve that by registering initial contributor, which is a child [Disposable] to [SearchEverywhereUI],
    // as a parent [Disposable] to a new contributor
    Disposer.register(parentContributor, newContributor)
    return newContributor
  }
}