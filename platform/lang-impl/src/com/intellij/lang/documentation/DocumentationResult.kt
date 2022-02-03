// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.util.AsyncSupplier
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

@Experimental
sealed interface DocumentationResult {

  sealed interface Data : DocumentationResult {

    fun html(html: @Nls String): Data

    /**
     * @param imageResolver allows resolving images by their URL in `<img src="url">` tags
     */
    fun imageResolver(imageResolver: DocumentationImageResolver?): Data

    /**
     * The scrolling is only executed on the initial showing, the anchor does not have an effect on [updates].
     *
     * @param anchor element `id` or link `name` in the [html] to scroll to,
     * or `null` to scroll to the top
     */
    fun anchor(anchor: String?): Data

    /**
     * @param externalUrl a URL to use in *External Documentation* action;
     * the URL will be appended to the bottom of the [html]
     */
    fun externalUrl(externalUrl: String?): Data
  }

  companion object {

    /**
     * @param html see [Data.html]
     */
    @JvmStatic
    fun documentation(html: @Nls String): Data {
      return DocumentationResultData(html)
    }

    /**
     * The [supplier]:
     * - will be invoked in background;
     * - is expected to return [documentation];
     * - is free to obtain a read action itself if needed.
     */
    fun asyncDocumentation(supplier: AsyncSupplier<DocumentationResult?>): DocumentationResult {
      return AsyncDocumentation(supplier)
    }

    /**
     * Same as another overload, but suitable for using from Java.
     * The [supplier] will be invoked under progress indicator.
     */
    @JvmStatic
    fun asyncDocumentation(supplier: Supplier<DocumentationResult?>): DocumentationResult {
      return AsyncDocumentation(supplier.asAsyncSupplier())
    }
  }
}
