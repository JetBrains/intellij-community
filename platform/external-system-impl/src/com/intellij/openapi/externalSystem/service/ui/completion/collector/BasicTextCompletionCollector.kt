// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion.collector

import com.intellij.openapi.application.ModalityState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal abstract class BasicTextCompletionCollector<T> private constructor() : TextCompletionCollector<T> {

  abstract override fun getCompletionVariants(): List<T>

  override fun collectCompletionVariants(modalityState: ModalityState, callback: (List<T>) -> Unit) {
    callback(getCompletionVariants())
  }

  companion object {

    fun <T> create(collect: () -> List<T>): TextCompletionCollector<T> {
      return object : BasicTextCompletionCollector<T>() {
        override fun getCompletionVariants(): List<T> {
          return collect()
        }
      }
    }
  }
}