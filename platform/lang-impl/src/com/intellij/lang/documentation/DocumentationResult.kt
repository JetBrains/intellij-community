// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.util.AsyncSupplier
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

@Experimental
sealed interface DocumentationResult {

  companion object {

    /**
     * @param anchor element `id` or link `name` in the [html] to scroll to, or `null` to scroll to the top
     * @return result of documentation computation
     */
    @JvmStatic
    @JvmOverloads
    fun documentation(
      html: @Nls String,
      anchor: String? = null,
      imageResolver: DocumentationImageResolver? = null,
    ): DocumentationResult {
      return DocumentationData(html, anchor, externalUrl = null, linkUrls = emptyList(), imageResolver)
    }

    /**
     * @param anchor an element `id` or a link `name` in the [html] to scroll to, or `null` to scroll to the top
     * @param externalUrl a URL to append to the bottom of the [html]
     */
    @JvmStatic
    @JvmOverloads
    fun externalDocumentation(
      html: @Nls String,
      anchor: String? = null,
      externalUrl: String,
      imageResolver: DocumentationImageResolver? = null,
    ): DocumentationResult {
      return DocumentationData(html, anchor, externalUrl, linkUrls = emptyList(), imageResolver)
    }

    /**
     * The [supplier]:
     * - will be invoked in background;
     * - is expected to return [documentation] or [externalDocumentation];
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
