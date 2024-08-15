// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.*
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.util.coroutines.childScope
import fleet.kernel.change
import fleet.kernel.rebase.*
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@Service
private class RemoteKernelScopeHolder(private val coroutineScope: CoroutineScope) {

  suspend fun createRemoteKernel(): RemoteKernel {
    val kernelService = KernelService.instance
    return RemoteKernelImpl(
      kernelService.kernel(),
      coroutineScope.childScope("RemoteKernelScope", kernelService.coroutineContext()),
      CommonInstructionSet.decoder(),
      KernelRpcSerialization,
    )
  }
}

internal class RemoteKernelProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteKernel>()) {
      runBlockingCancellable {
        ApplicationManager.getApplication().service<RemoteKernelScopeHolder>().createRemoteKernel()
      }
    }
  }
}

internal class BackendKernelService(coroutineScope: CoroutineScope) : KernelService {

  private val contextDeferred: CompletableDeferred<CoroutineContext> = CompletableDeferred()

  override suspend fun coroutineContext(): CoroutineContext {
    return contextDeferred.await()
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
