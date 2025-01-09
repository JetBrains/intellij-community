// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SearchEverywhereAsyncContributor<Item> {
  val synchronousContributor: SearchEverywhereContributor<Item>

  fun fetchElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: AsyncProcessor<Item>
  ) {
    synchronousContributor.fetchElements(pattern, progressIndicator) { t ->
      runBlockingCancellable {
        consumer.process(t)
      }
    }
  }

}

@Internal
interface AsyncProcessor<T> {
  suspend fun process(t: T): Boolean
}
