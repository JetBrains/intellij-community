// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * [timeoutRunBlocking] with an extra [CoroutineScope] — the analogue of `runTest`'s `backgroundScope`, which
 * [timeoutRunBlocking] does not provide. The scope is passed to [action] and cancelled once it returns, so
 * long-lived collectors and view models launched into it don't keep the test coroutine from completing.
 */
@TestOnly
@ApiStatus.Internal
fun timeoutRunBlockingWithBackgroundScope(
  action: suspend CoroutineScope.(backgroundScope: CoroutineScope) -> Unit,
): Unit = timeoutRunBlocking {
  val backgroundScope = childScope("test background scope")
  try {
    action(backgroundScope)
  }
  finally {
    backgroundScope.cancel()
  }
}
