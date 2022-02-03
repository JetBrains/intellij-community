// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.progress.withJob
import com.intellij.util.AsyncSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.function.Supplier

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
    withJob {
      this@asAsyncSupplier.get()
    }
  }
}
