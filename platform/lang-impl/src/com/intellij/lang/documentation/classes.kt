// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation

import com.intellij.openapi.progress.withJob
import com.intellij.util.AsyncSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Supplier

@VisibleForTesting
class DocumentationData internal constructor(
  val html: @Nls String,
  val anchor: String?,
  val externalUrl: String?,
  val imageResolver: DocumentationImageResolver?
) : DocumentationResult

internal class AsyncDocumentation(
  val supplier: AsyncSupplier<DocumentationResult?>
) : DocumentationResult

internal class ResolvedTarget(
  val target: DocumentationTarget,
) : LinkResult

internal class ContentUpdates(
  val updates: Flow<String>,
) : LinkResult

internal fun <X> Supplier<X>.asAsyncSupplier(): AsyncSupplier<X> = {
  withContext(Dispatchers.IO) {
    withJob {
      this@asAsyncSupplier.get()
    }
  }
}
