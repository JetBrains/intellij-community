// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion.collector

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autolink.launch
import com.intellij.openapi.externalSystem.service.ui.completion.cache.AsyncLocalCache
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.ModificationTracker
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal abstract class AsyncTextCompletionCollector<T> private constructor(
  private val parentDisposable: Disposable
) : TextCompletionCollector<T> {

  protected abstract val completionModificationTracker: ModificationTracker

  protected abstract suspend fun collectCompletionVariants(): List<T>

  private val completionVariantCache = AsyncLocalCache<List<T>>()

  override fun getCompletionVariants(): List<T> {
    return completionVariantCache.getValue() ?: emptyList()
  }

  override fun collectCompletionVariants(modalityState: ModalityState, callback: (List<T>) -> Unit) {
    CoroutineScopeService.coroutineScope.launch(parentDisposable, modalityState.asContextElement()) {
      val modificationCount = completionModificationTracker.modificationCount
      val completionVariants = completionVariantCache.getOrCreateValue(modificationCount) {
        collectCompletionVariants()
      }
      withContext(Dispatchers.EDT) {
        blockingContext {
          callback(completionVariants)
        }
      }
    }
  }

  @Service
  private class CoroutineScopeService(private val scope: CoroutineScope) {
    companion object {
      val coroutineScope: CoroutineScope
        get() = ApplicationManager.getApplication().service<CoroutineScopeService>().scope
    }
  }

  companion object {

    fun <T> create(
      parentDisposable: Disposable,
      collect: suspend () -> List<T>
    ): TextCompletionCollector<T> {
      return create(ModificationTracker.NEVER_CHANGED, parentDisposable, collect)
    }

    fun <T> create(
      modificationTracker: ModificationTracker,
      parentDisposable: Disposable,
      collect: suspend () -> List<T>
    ): TextCompletionCollector<T> {
      return object : AsyncTextCompletionCollector<T>(parentDisposable) {
        override val completionModificationTracker = modificationTracker
        override suspend fun collectCompletionVariants() = collect()
      }
    }
  }
}