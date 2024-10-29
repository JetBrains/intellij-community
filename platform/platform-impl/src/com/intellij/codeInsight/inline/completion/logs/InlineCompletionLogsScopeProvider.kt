// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
internal class InlineCompletionLogsScopeProvider(internal val cs: CoroutineScope) {
  companion object {
    @ApiStatus.Internal
    fun getInstance() = service<InlineCompletionLogsScopeProvider>()
  }
}