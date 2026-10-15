// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlayPassContext {
  private val current = ThreadLocal<Map<String, Boolean>?>()

  @JvmStatic
  fun get(): Map<String, Boolean>? = current.get()

  @JvmStatic
  fun run(optionVisibility: Map<String, Boolean>?, runnable: Runnable) {
    val previous = current.get()
    current.set(optionVisibility)
    try {
      runnable.run()
    }
    finally {
      if (previous != null) {
        current.set(previous)
      }
      else {
        current.remove()
      }
    }
  }
}
