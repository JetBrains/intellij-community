// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ReadActionCachedValue<T>(private val provider: () -> T) {
  
  fun getCachedOrEvaluate(): T {
    val processingContext = ReadActionCache.getInstance().processingContext ?: return provider.invoke()
    processingContext.get(this)?.let {
      @Suppress("UNCHECKED_CAST")
      return it as T
    }
    val result = provider.invoke()
    processingContext.put(this as Any, result as Any)
    return result
  }
}