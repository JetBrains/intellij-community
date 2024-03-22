// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.monolith

import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.CommonInstructionSet
import com.intellij.platform.kernel.util.KernelRpcSerialization
import com.intellij.platform.kernel.util.ReadTracker
import com.intellij.platform.kernel.util.withKernel
import fleet.kernel.Kernel
import fleet.kernel.kernel
import fleet.kernel.rebase.LeaderKernelMiddleware
import fleet.kernel.rebase.encoder
import fleet.kernel.rete.Rete
import kotlinx.coroutines.*

internal class MonolithKernelService(coroutineScope: CoroutineScope) : KernelService {
  override val kernel: Kernel
  override val rete: Rete

  init {
    val kernelDeferred: CompletableDeferred<Kernel> = CompletableDeferred()
    val reteDeferred : CompletableDeferred<Rete> = CompletableDeferred()

    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      withKernel(middleware = LeaderKernelMiddleware(KernelRpcSerialization, CommonInstructionSet.encoder())) {
        kernelDeferred.complete(kernel())
        reteDeferred.complete(currentCoroutineContext()[Rete]!!)
        ReadTracker.subscribeForChanges()
      }
    }
    kernel = kernelDeferred.getCompleted()
    rete = reteDeferred.getCompleted()
  }
}

