// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.util.coroutines.attachAsChildTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Entry point to operations which work with the kernel.
 *
 * Implementation note:
 * this will become no-op once the kernel becomes a part of the container itself,
 * i.e., once the kernel is added to all coroutines by default.
 */
suspend fun <T> withKernel(action: suspend CoroutineScope.() -> T): T {
  val kernelScope = KernelService.instance.kernelCoroutineScope.await()
  val kernelContext = kernelScope.coroutineContext.kernelCoroutineContext()
  return withContext(kernelContext) {
    attachAsChildTo(kernelScope)
    action()
  }
}
