// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.*
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiProvider.RemoteApiDescriptor
import com.intellij.platform.util.coroutines.childScope
import fleet.kernel.change
import fleet.kernel.rebase.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

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

  private val contextDeferred: CompletableDeferred<CoroutineContext> = CompletableDeferred()

  override val coroutineContext: CoroutineContext
    get() = runBlocking {
      contextDeferred.await()
    }

  init {
    coroutineScope.launch {
      withKernel(middleware = LeaderKernelMiddleware(KernelRpcSerialization, CommonInstructionSet.encoder())) {
        change {
          initWorkspaceClock()
        }
        contextDeferred.complete(currentCoroutineContext().kernelCoroutineContext())
        ReadTracker.subscribeForChanges()
      }
    }
  }
}
