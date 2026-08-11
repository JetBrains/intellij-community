// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors

import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * - Store value obtained from [supplier] in a field for fast access,
 * - Clear/recalc when the scheme manager was modified (i.e. when its [com.intellij.openapi.editor.colors.EditorColorsManager.getSchemeModificationCounter] changed)
 */
@ApiStatus.Internal
class CachedVersionedColorScheme<T>(val supplier: Supplier<T>) {
  @Volatile
  private var cached: ValueAndModCount<T>? = null

  fun get(): T {
    val modificationCount = EditorColorsManager.getInstance()?.schemeModificationCounter ?: 0
    var cached = cached
    val upToDate = cached != null && cached.modificationCount == modificationCount
    if (!upToDate) {
      cached = ValueAndModCount(supplier.get(), modificationCount)
      this.cached = cached
    }
    return cached.value
  }
}
private data class ValueAndModCount<T>(val value: T, val modificationCount:Long)
