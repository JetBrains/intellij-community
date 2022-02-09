// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.util.AsyncSupplier
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.function.Supplier

@Experimental
sealed interface DocumentationResult {

  sealed interface Data : DocumentationResult {

    fun html(html: @Nls String): Data

    /**
     * @param images map from `url` of `<img src="url">` tag to an image
     */
    fun images(images: Map<String, Image>): Data

    /**
     * @param content [html] with [images]
     */
    fun content(content: DocumentationContent): Data

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
      return documentation(DocumentationContent.content(html))
    }

    /**
     * @param content see [Data.content]
     */
    @JvmStatic
    fun documentation(content: DocumentationContent): Data {
      return DocumentationResultData(content as DocumentationContentData)
    }

    /**
     * The [supplier]:
     * - will be invoked in background;
     * - is free to obtain a read action itself if needed.
     */
    fun asyncDocumentation(supplier: AsyncSupplier<Data?>): DocumentationResult {
      return AsyncDocumentation(supplier)
    }

    /**
     * Same as another overload, but suitable for using from Java.
     * The [supplier] will be invoked under progress indicator.
     */
    @JvmStatic
    fun asyncDocumentation(supplier: Supplier<Data?>): DocumentationResult {
      return AsyncDocumentation(supplier.asAsyncSupplier())
    }
  }
}
