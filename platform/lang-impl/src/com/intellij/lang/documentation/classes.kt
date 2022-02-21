// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.progress.blockingContext
import com.intellij.util.AsyncSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.function.Consumer
import java.util.function.Supplier

internal data class DocumentationContentData internal constructor(
  val html: @Nls String,
  val imageResolver: DocumentationImageResolver?,
) : DocumentationContent

internal data class LinkData(
  val externalUrl: String? = null,
  val linkUrls: List<String> = emptyList(),
)

internal class AsyncDocumentation(
  val supplier: AsyncSupplier<DocumentationResult.Data?>
) : DocumentationResult

internal class ResolvedTarget(
  val target: DocumentationTarget,
) : LinkResolveResult

internal class AsyncLinkResolveResult(
  val supplier: AsyncSupplier<LinkResolveResult.Async?>,
) : LinkResolveResult

internal class AsyncResolvedTarget(
  val pointer: Pointer<out DocumentationTarget>,
) : LinkResolveResult.Async

internal fun <X> Supplier<X>.asAsyncSupplier(): AsyncSupplier<X> = {
  withContext(Dispatchers.IO) {
    blockingContext {
      this@asAsyncSupplier.get()
    }
  }
}

internal fun imageResolver(map: Map<String, Image>): DocumentationImageResolver? {
  if (map.isEmpty()) {
    return null
  }
  return DocumentationImageResolver(map.toMap()::get)
}

@Suppress("OPT_IN_USAGE")
internal fun <X> Consumer<in Consumer<in X>>.asFlow(): Flow<X> {
  val flow = channelFlow {
    blockingContext {
      accept { content ->
        check(trySend(content).isSuccess) // sanity check
      }
    }
  }
  return flow
    .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    .flowOn(Dispatchers.IO)
}
