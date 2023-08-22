// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
fun interface ContentUpdater {

  /**
   * The returned flow is collected in [IO context][kotlinx.coroutines.Dispatchers.IO].
   * Clicking another link, or closing the browser, or resetting the browser cancels the flow collection.
   * Each emitted update replaces the browser content. Scrolling position is preserved in the browser when the update is applied.
   *
   * @return a series of content updates,
   * which will continuously replace browser content until the returned flow is fully collected
   */
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  fun prepareContentUpdates(currentContent: @Nls String): Flow<@Nls String>
}
