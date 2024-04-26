// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation

import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.model.Pointer
import com.intellij.openapi.progress.blockingContext
import com.intellij.psi.PsiElement
import com.intellij.util.AsyncSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.function.Supplier

@Internal
internal data class DocumentationContentData internal constructor(
  val html: @Nls String,
  val imageResolver: DocumentationImageResolver?,
  val definitionDetails: String? = null,
  val targetElement: PsiElement? = null
) : DocumentationContent

@Internal
internal data class LinkData(
  val externalUrl: String? = null,
  val linkUrls: List<String> = emptyList(),
)

@Internal
class AsyncDocumentation(
  val supplier: AsyncSupplier<DocumentationResult.Documentation?>
) : DocumentationResult

@Internal
internal class ResolvedTarget(
  val target: DocumentationTarget,
) : LinkResolveResult

@Internal
internal class AsyncLinkResolveResult(
  val supplier: AsyncSupplier<LinkResolveResult.Async?>,
) : LinkResolveResult

@Internal
internal class AsyncResolvedTarget(
  val pointer: Pointer<out DocumentationTarget>,
) : LinkResolveResult.Async

@Internal
internal fun <X> Supplier<X>.asAsyncSupplier(): AsyncSupplier<X> = {
  withContext(Dispatchers.IO) {
    blockingContext {
      this@asAsyncSupplier.get()
    }
  }
}

@Internal
internal fun imageResolver(map: Map<String, Image>): DocumentationImageResolver? {
  if (map.isEmpty()) {
    return null
  }
  return DocumentationImageResolver(map.toMap()::get)
}

@Internal
internal fun BlockingDocumentationContentFlow.asFlow(): Flow<DocumentationContent> {
  val flow = channelFlow {
    blockingContext {
      collectBlocking { content ->
        check(trySend(content).isSuccess) // sanity check
      }
    }
  }
  return flow
    .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    .flowOn(Dispatchers.IO)
}
