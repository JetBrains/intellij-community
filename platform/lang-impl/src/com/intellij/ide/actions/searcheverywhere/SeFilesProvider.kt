// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.ItemWithPresentation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive

class SeFileItem(val legacyItem: ItemWithPresentation<*>) : SeItem {
  override fun weight(): Int = 0
  override fun presentation(): SeItemPresentation = SeTextItemPresentation(legacyItem.presentation.presentableText)
}

class SeFilesProvider(val project: Project, private val legacyContributor: SearchEverywhereAsyncContributor<Any?>): SeItemsProvider {
  override val id: String
    get() = "com.intellij.FileSearchEverywhereItemProvider"

  override fun getItems(params: SeParams): Flow<SeItem> {
    val text = (params as? SeTextSearchParams)?.text ?: return emptyFlow()

    return channelFlow {
      val collector = this

      coroutineToIndicator {
        val indicator = ProgressManager.getGlobalProgressIndicator()

        legacyContributor.fetchElements(text, indicator, object: AsyncProcessor<Any?> {
          override suspend fun process(t: Any?): Boolean {
            val legacyItem = t as? ItemWithPresentation<*> ?: return true
            collector.send(SeFileItem(legacyItem))
            return coroutineContext.isActive
          }
        })
      }
    }
  }

  companion object {
    const val ID = "com.intellij.FileSearchEverywhereItemProvider"
  }
}