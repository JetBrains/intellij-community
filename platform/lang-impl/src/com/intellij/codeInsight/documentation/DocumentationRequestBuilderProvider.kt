// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import java.net.URL

interface DocumentationRequestBuilderProvider {
  /**
   * Creates and configures a [RequestBuilder] for the given [context] and platform-generated [url].
   *
   * The [URL] of the returned [RequestBuilder] may be different from [url]. A return value of `null`
   * indicates that this [DocumentationRequestBuilderProvider] is not applicable.
   */
  fun createRequestBuilder(context: RequestContext, url: URL): RequestBuilder?

  data class RequestContext(val element: PsiElement?)

  companion object {
    val EP_NAME: ExtensionPointName<DocumentationRequestBuilderProvider> =
      ExtensionPointName.create("com.intellij.documentationRequestBuilderProvider")

    @JvmStatic
    fun createRequestBuilder(context: RequestContext, url: URL): RequestBuilder =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.createRequestBuilder(context, url) }
      ?: HttpRequests.request(url.toString())
  }
}
