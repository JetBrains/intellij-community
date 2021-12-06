// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
sealed interface LinkResult {

  companion object {

    /**
     * @return a result, which makes the browser load the documentation for the [target]
     */
    @JvmStatic
    fun resolvedTarget(target: DocumentationTarget): LinkResult {
      return ResolvedTarget(target)
    }

    /**
     * This result should be used in clients wishing to load some additional data when a link is activated,
     * and then update the browser content with the loaded data.
     * The [updates] flow is collected in [IO context][kotlinx.coroutines.Dispatchers.IO].
     * Clicking another link, or closing the browser, or resetting the browser cancels the flow collection.
     * Each emitted update replaces the browser content. Scrolling position is preserved in the browser when the update is applied.
     *
     * @return a result, which represents a [series of content updates][updates],
     * which will continuously replace browser content until the returned flow is fully collected
     */
    @JvmStatic
    fun contentUpdates(updates: Flow<String>): LinkResult {
      return ContentUpdates(updates)
    }
  }
}
