// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.function.Supplier

/**
 * This is a union type: `Documentation | suspend () -> Documentation?`.
 */
sealed interface DocumentationResult {

  sealed interface Documentation : DocumentationResult {

    fun html(html: @Nls String): Documentation

    /**
     * @param images map from `url` of `<img src="url">` tag to an image
     */
    fun images(images: Map<String, Image>): Documentation

    /**
     * @param content [html] with [images]
     */
    fun content(content: DocumentationContent): Documentation

    /**
     * The scrolling is only executed on the initial showing, the anchor does not have an effect on [updates].
     *
     * @param anchor element `id` or link `name` in the [html] to scroll to,
     * or `null` to scroll to the top
     */
    fun anchor(anchor: String?): Documentation

    /**
     * @param externalUrl a URL to use in *External Documentation* action;
     * the URL will be appended to the bottom of the [html]
     */
    fun externalUrl(externalUrl: String?): Documentation

    /**
     * Expandable section under element definition
     */
    fun definitionDetails(details: String?): Documentation

    /**
     * [Updates][updates] continuously replace the browser content until the flow is fully collected.
     * Clicking another link, closing the browser, resetting the browser, going back or forward cancels the flow collection.
     * Scrolling position is preserved in the browser when the update is applied, i.e. [anchor] does not have any effect on updates.
     */
    fun updates(updates: Flow<DocumentationContent>): Documentation

    /**
     * Same as asynchronous overload, but blocking.
     * The [updates] are collected in [IO context][kotlinx.coroutines.Dispatchers.IO].
     */
    @Experimental
    fun blockingUpdates(updates: BlockingDocumentationContentFlow): Documentation
  }

  companion object {

    /**
     * @param html see [Documentation.html]
     */
    @JvmStatic
    fun documentation(html: @Nls String): Documentation {
      return documentation(DocumentationContent.content(html))
    }

    /**
     * @param content see [Documentation.content]
     */
    @JvmStatic
    fun documentation(content: DocumentationContent): Documentation {
      return DocumentationData(content as DocumentationContentData)
    }

    /**
     * The [supplier]:
     * - will be invoked in background;
     * - is free to obtain a read action itself if needed.
     */
    fun asyncDocumentation(supplier: suspend () -> Documentation?): DocumentationResult {
      return AsyncDocumentation(supplier)
    }

    /**
     * Same as another overload, but suitable for using from Java.
     * The [supplier] will be invoked under progress indicator.
     */
    @JvmStatic
    fun asyncDocumentation(supplier: Supplier<Documentation?>): DocumentationResult {
      return AsyncDocumentation(supplier.asAsyncSupplier())
    }
  }
}
