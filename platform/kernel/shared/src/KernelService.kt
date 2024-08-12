// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.openapi.application.ApplicationManager
import fleet.kernel.DbSource
import fleet.kernel.Kernel
import fleet.kernel.rete.Rete
import fleet.kernel.withCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.coroutines.CoroutineContext

interface KernelService {

  val kernel: Kernel
  val rete: Rete

  companion object {

    val instance: KernelService
      get() = ApplicationManager.getApplication().getService(KernelService::class.java)

    val kernelCoroutineContext: CoroutineContext
      get() = instance.kernel + instance.rete + DbSource(instance.kernel.dbState, instance.kernel.toString())

    fun <T> CoroutineScope.saga(condition: () -> Boolean = { true }, block: suspend CoroutineScope.() -> T): Deferred<T> {
      return async(kernelCoroutineContext) {
        withCondition(condition, block)
      }
    }
  }
}
