// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.DbSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Allows reading from the current DB state.
 * It may be useful when you don't know whether function will be called in EDT context (where DB state is propagated)
 * or in some background thread, where DB state is not propagated by default.
 *
 * Avoid using this API if your code is called in EDT.
 *
 * NB: This is a delicate API! Use with caution, since using [f] may be called on the "old" DB state where some latest changes are not applied
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> withLastKnownDb(body: () -> T): T {
  return asOf(KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext[DbSource.ContextElement]!!.dbSource.latest) {
    body()
  }
}