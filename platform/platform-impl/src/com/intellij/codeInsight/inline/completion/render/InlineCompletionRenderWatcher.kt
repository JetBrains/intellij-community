// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import java.util.*

@Service
class InlineCompletionRenderWatcher {

  private val registered = Collections.newSetFromMap(IdentityHashMap<InlineCompletionBlock, Boolean>())

  @RequiresEdt
  fun register(element: InlineCompletionBlock) {
    if (application.isUnitTestMode) {
      registered += element
    }
  }

  @RequiresEdt
  fun dispose(element: InlineCompletionBlock) {
    if (application.isUnitTestMode) {
      registered -= element
    }
  }

  @TestOnly
  fun assertNothingRenders() {
    if (application.isUnitTestMode) {
      assert(registered.isEmpty()) {
        "Elements $registered were not disposed, and they still render."
      }
    }
  }

  companion object {
    fun getInstance(): InlineCompletionRenderWatcher = service()
  }
}
