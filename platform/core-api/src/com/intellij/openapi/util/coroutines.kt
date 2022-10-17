// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun ActionCallback.await(): Unit = suspendCancellableCoroutine { continuation ->
  doWhenDone {
    continuation.resume(Unit)
  }.doWhenRejected { message ->
    continuation.resumeWithException(RuntimeException(message))
  }
}
