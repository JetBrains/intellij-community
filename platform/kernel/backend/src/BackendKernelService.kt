// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.CommonInstructionSet
import com.intellij.platform.kernel.util.KernelRpcSerialization
import com.intellij.platform.kernel.util.ReadTracker
import com.intellij.platform.kernel.util.withKernel
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiProvider.RemoteApiDescriptor
import com.intellij.platform.util.coroutines.childScope
import fleet.kernel.Kernel
import fleet.kernel.kernel
import fleet.kernel.rebase.*
import fleet.kernel.rete.Rete
import kotlinx.coroutines.*

@Service
private class RemoteKernelScopeHolder(coroutineScope: CoroutineScope) {
  val remoteKernelScope: CoroutineScope = coroutineScope.childScope("RemoteKernelScope", KernelService.kernelCoroutineContext)
}

internal class RemoteKernelProvider : RemoteApiProvider {

  override fun getApis(): List<RemoteApiDescriptor<*>> {
    return listOf(RemoteApiDescriptor(RemoteKernel::class) {
      RemoteKernelImpl(
        KernelService.instance.kernel,
        ApplicationManager.getApplication().service<RemoteKernelScopeHolder>().remoteKernelScope,
        CommonInstructionSet.decoder(),
        KernelRpcSerialization
      )
    })
  }
}

internal class BackendKernelService(coroutineScope: CoroutineScope) : KernelService {

  private val kernelDeferred: CompletableDeferred<Kernel> = CompletableDeferred()
  private val reteDeferred: CompletableDeferred<Rete> = CompletableDeferred()

  init {
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      withKernel(middleware = LeaderKernelMiddleware(KernelRpcSerialization, CommonInstructionSet.encoder())) {
        coroutineScope {
          kernelDeferred.complete(currentCoroutineContext()[Kernel]!!)
          reteDeferred.complete(currentCoroutineContext()[Rete]!!)
          kernel().changeSuspend {
            initWorkspaceClock()
          }
          ReadTracker.subscribeForChanges()
        }
      }
    }
  }

  override val kernel: Kernel
    get() = runBlocking {
      kernelDeferred.await()
    }

  override val rete: Rete
    get() = runBlocking {
      reteDeferred.await()
    }
}
