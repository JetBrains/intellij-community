// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Entry point to operations which work with the kernel.
 *
 * Implementation note:
 * this will become no-op once the kernel becomes a part of the container itself,
 * i.e., once the kernel is added to all coroutines by default.
 */
suspend fun <T> withKernel(action: suspend CoroutineScope.() -> T): T {
  val kernelContext = kernelCoroutineContext()
  return withContext(kernelContext, action)
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun kernelCoroutineContext(): CoroutineContext {
  return KernelService.instance
    .kernelCoroutineContext
    .getCompleted()
}
