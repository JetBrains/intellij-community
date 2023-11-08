// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation

import com.intellij.platform.backend.documentation.DocumentationResult.Documentation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Image

@Internal
@VisibleForTesting
data class DocumentationData internal constructor(
  internal val content: DocumentationContentData,
  internal val links: LinkData = LinkData(),
  val anchor: String? = null,
  internal val updates: Flow<DocumentationContentData> = emptyFlow(),
) : Documentation {

  val html: String get() = content.html

  override fun html(html: String): Documentation {
    return content(content.copy(html = html))
  }

  override fun images(images: Map<String, Image>): Documentation {
    return content(content.copy(imageResolver = imageResolver(images)))
  }

  override fun content(content: DocumentationContent): Documentation {
    return copy(content = content as DocumentationContentData)
  }

  override fun anchor(anchor: String?): Documentation {
    return copy(anchor = anchor)
  }

  override fun definitionDetails(details: String?): Documentation {
    return content(content.copy(definitionDetails = details))
  }

  override fun externalUrl(externalUrl: String?): Documentation {
    return copy(links = links.copy(externalUrl = externalUrl))
  }

  override fun updates(updates: Flow<DocumentationContent>): Documentation {
    @Suppress("UNCHECKED_CAST")
    return copy(updates = updates as Flow<DocumentationContentData>)
  }

  override fun blockingUpdates(updates: BlockingDocumentationContentFlow): Documentation {
    return updates(updates.asFlow())
  }
}
