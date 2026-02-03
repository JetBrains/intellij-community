// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index

import com.intellij.util.ThrowableConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

internal fun executeWithScope(task: ThrowableConsumer<CoroutineScope, Throwable>) {
  runBlocking {
    task.consume(this)
  }
}