// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
fun interface QueryExecutionInterceptor {

  /**
   * Implementation is expected to call [invocation] and return its value.
   */
  fun intercept(invocation: () -> Boolean): Boolean
}
