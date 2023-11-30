// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class SingleCoroutineLauncher(private val cs: CoroutineScope) {

  private val currentTaskKey = MutableStateFlow<UUID?>(null)
  val busy: Flow<Boolean> = currentTaskKey.map { it != null }

  fun launch(context: CoroutineContext = EmptyCoroutineContext,
             start: CoroutineStart = CoroutineStart.DEFAULT,
             block: suspend CoroutineScope.() -> Unit) {
    val key = UUID.randomUUID()
    if (!currentTaskKey.compareAndSet(null, key)) return
    cs.launch(context, start) {
      try {
        block()
      }
      finally {
        currentTaskKey.compareAndSet(key, null)
      }
    }
  }
}