// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// PluginManagerCore in core, where Java 8 is required, so, we cannot move tracer.kt to util
@Internal
@Experimental
interface CoroutineTracerShim {
  companion object {
    var coroutineTracer: CoroutineTracerShim = object : CoroutineTracerShim {
      override suspend fun getTraceActivity() = null
      override fun rootTrace() = EmptyCoroutineContext

      override suspend fun <T> span(name: String, context: CoroutineContext, action: suspend CoroutineScope.() -> T): T {
        return withContext(context + CoroutineName(name), action)
      }
    }
  }

  suspend fun getTraceActivity(): Activity?

  fun rootTrace(): CoroutineContext

  suspend fun <T> span(name: String, context: CoroutineContext = EmptyCoroutineContext, action: suspend CoroutineScope.() -> T): T
}