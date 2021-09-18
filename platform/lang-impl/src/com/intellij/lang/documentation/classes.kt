// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation

import com.intellij.util.AsyncSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal class DocumentationData(
  val html: @Nls String,
  val anchor: String?,
  val externalUrl: String?,
) : DocumentationResult

internal class AsyncDocumentation(
  val supplier: AsyncSupplier<DocumentationResult?>
) : DocumentationResult

internal fun <X> Supplier<X>.asAsyncSupplier(): AsyncSupplier<X> = {
  withContext(Dispatchers.IO) {
    this@asAsyncSupplier.get()
  }
}
