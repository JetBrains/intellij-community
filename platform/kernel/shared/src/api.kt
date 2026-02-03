// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.DbSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Allows operating with RhizomeDB in coroutines where Kernel is not provided to the context.
 * RhizomeDB context is propagated by default to EDT and Services' coroutine scopes.
 *
 * Use it only for cases when RhizomeDB context is not propagated (e.g. when `CoroutineScope(...)` or `GlobalScope.child(...)` are used).
 */
@Deprecated("Kernel context is propagated automatically to EDT and Services' coroutine scopes, so `withKernel` shouldn't be needed")
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