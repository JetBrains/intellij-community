// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.lang.documentation.DocumentationResult.Data
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
data class DocumentationResultData internal constructor(
  val html: @Nls String,
  val anchor: String? = null,
  val externalUrl: String? = null,
  val linkUrls: List<String> = emptyList(),
  val imageResolver: DocumentationImageResolver? = null,
) : Data {

  override fun html(html: String): Data {
    return copy(html = html)
  }

  override fun imageResolver(imageResolver: DocumentationImageResolver?): Data {
    return copy(imageResolver = imageResolver)
  }

  override fun anchor(anchor: String?): Data {
    return copy(anchor = anchor)
  }

  override fun externalUrl(externalUrl: String?): Data {
    return copy(externalUrl = externalUrl)
  }
}
