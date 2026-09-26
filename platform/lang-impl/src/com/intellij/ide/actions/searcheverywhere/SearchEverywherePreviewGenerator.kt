// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.usageView.UsageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SearchEverywherePreviewGenerator(
  val project: Project,
  private val updatePreviewPanel: Consumer<List<UsageInfo>?>? = null,
  publishPreviewTime: BiConsumer<Any, Duration>,
) : Disposable {
  private val requestSharedFlow = MutableSharedFlow<Any?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val previewFetchingScope: CoroutineScope?

  init {
    val seCoroutineScope = project.serviceOrNull<SearchEverywhereCoroutineScopeService>()?.coroutineScope
    previewFetchingScope = seCoroutineScope?.childScope("SearchEverywherePreviewGenerator scope")

    val fetcher = SearchEverywherePreviewFetcher(project, publishPreviewTime, this)

    previewFetchingScope?.launch {
      requestSharedFlow.mapLatest { selectedValue ->
        fetcher.fetchPreview(selectedValue)
      }.collectLatest { usageInfos ->
        updatePreviewPanel?.let {
          withContext(Dispatchers.EDT) {
            updatePreviewPanel.accept(usageInfos)
          }
        }
      }
    }
  }

  fun schedulePreview(selectedValue: Any) {
    check(requestSharedFlow.tryEmit(selectedValue))
  }

  override fun dispose() {
    previewFetchingScope?.cancel("SearchEverywherePreviewGenerator disposed")
  }
}