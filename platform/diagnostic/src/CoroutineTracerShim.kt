// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// PluginManagerCore in core, where Java 8 is required, so, we cannot move tracer.kt to util
interface CoroutineTracerShim {
  companion object {
    var coroutineTracerShim: CoroutineTracerShim = object : CoroutineTracerShim {
      override suspend fun getTraceActivity() = null

      override suspend fun <T> subTask(name: String, context: CoroutineContext, action: suspend CoroutineScope.() -> T): T {
        return withContext(context + CoroutineName(name), action)
      }
    }
  }

  suspend fun getTraceActivity(): Activity?

  suspend fun <T> subTask(name: String, context: CoroutineContext = EmptyCoroutineContext, action: suspend CoroutineScope.() -> T): T
}