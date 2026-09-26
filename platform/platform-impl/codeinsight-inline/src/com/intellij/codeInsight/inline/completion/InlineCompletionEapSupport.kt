// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@Service(Service.Level.APP)
class InlineCompletionEapSupport {

  private var mockEap: Boolean? = null

  @VisibleForTesting
  fun setMockEap(isEap: Boolean) {
    mockEap = isEap
  }

  @VisibleForTesting
  fun clearMock() {
    mockEap = null
  }

  fun isEap(): Boolean {
    return mockEap ?: ApplicationManager.getApplication().isEAP
  }

  companion object {
    @JvmStatic
    fun getInstance(): InlineCompletionEapSupport = service()
  }
}