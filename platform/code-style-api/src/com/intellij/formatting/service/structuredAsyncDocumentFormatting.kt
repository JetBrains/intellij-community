// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StructuredAsyncDocumentFormatting")
@file:ApiStatus.Experimental
package com.intellij.formatting.service

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.openapi.progress.withCurrentThreadCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * This scope does not return until all [AsyncDocumentFormattingService]s invoked inside have finished formatting.
 *
 * Usage:
 * ```kotlin
 * val processor = ReformatCodeProcessor(...)
 * structuredAsyncDocumentFormattingScope {
 *   coroutineToIndicator { indicator ->
 *     processor.processFilesUnderProgress(indicator)
 *   }
 * }
 * ```
 */
suspend fun <R> structuredAsyncDocumentFormattingScope(action: suspend CoroutineScope.() -> R): R {
  return withContext(StructuredAsyncDocumentFormattingMarker) {
    val (result, job) = withCurrentThreadCoroutineScope {
      action()
    }
    job.join()
    result
  }
}

@ApiStatus.Internal
fun CoroutineContext.isStructuredAsyncDocumentFormatting(): Boolean =
  get(StructuredAsyncDocumentFormattingMarker.Key) != null

private object StructuredAsyncDocumentFormattingMarker : AbstractCoroutineContextElement(Key), IntelliJContextElement {
  object Key : CoroutineContext.Key<StructuredAsyncDocumentFormattingMarker>
}
