// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.test.assertEquals

object FlowTestUtil {
  suspend inline fun <T> Flow<T>.testCollect(crossinline runCollecting: suspend () -> Unit): List<T> {
    return coroutineScope {
      val values = mutableListOf<T>()
      val collectorJob = launch {
        collect {
          values.add(it)
        }
      }
      runCollecting()
      collectorJob.cancel()
      values
    }
  }

  suspend inline fun <T> Flow<T>.assertEmits(vararg shouldEmit: T, crossinline runCollecting: suspend () -> Unit) {
    val emitted = testCollect(runCollecting)
    assertEquals(shouldEmit.toList(), emitted)
  }
}