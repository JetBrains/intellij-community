// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AsyncDocumentFormattingSupportFactoryImpl(private val cs: CoroutineScope) : AsyncDocumentFormattingSupportFactory {
  override fun create(service: AsyncDocumentFormattingService): AsyncDocumentFormattingSupport {
    return AsyncDocumentFormattingSupportImpl(service, cs)
  }
}