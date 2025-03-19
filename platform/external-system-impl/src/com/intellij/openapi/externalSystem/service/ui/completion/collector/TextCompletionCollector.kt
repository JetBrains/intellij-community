// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion.collector

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.OverrideOnly
interface TextCompletionCollector<T> {

  fun getCompletionVariants(): List<T>

  fun collectCompletionVariants(modalityState: ModalityState, callback: (List<T>) -> Unit)

  companion object {

    fun <T> basic(
      collect: () -> List<T>
    ): TextCompletionCollector<T> {
      return BasicTextCompletionCollector.create(collect)
    }

    fun <T> async(
      parentDisposable: Disposable,
      collect: suspend () -> List<T>
    ): TextCompletionCollector<T> {
      return AsyncTextCompletionCollector.create(parentDisposable, collect)
    }

    fun <T> async(
      modificationTracker: ModificationTracker,
      parentDisposable: Disposable,
      collect: suspend () -> List<T>
    ): TextCompletionCollector<T> {
      return AsyncTextCompletionCollector.create(modificationTracker, parentDisposable, collect)
    }
  }
}