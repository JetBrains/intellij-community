// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.lang.documentation.DocumentationResult.Data
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Image

@VisibleForTesting
data class DocumentationResultData internal constructor(
  internal val content: DocumentationContentData,
  internal val links: LinkData = LinkData(),
  val anchor: String? = null,
) : Data {

  val html: String get() = content.html

  override fun html(html: String): Data {
    return content(content.copy(html = html))
  }

  override fun images(images: Map<String, Image>): Data {
    return content(content.copy(imageResolver = imageResolver(images)))
  }

  override fun content(content: DocumentationContent): Data {
    return copy(content = content as DocumentationContentData)
  }

  override fun anchor(anchor: String?): Data {
    return copy(anchor = anchor)
  }

  override fun externalUrl(externalUrl: String?): Data {
    return copy(links = links.copy(externalUrl = externalUrl))
  }
}
