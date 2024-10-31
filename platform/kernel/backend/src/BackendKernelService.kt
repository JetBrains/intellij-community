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
import fleet.kernel.transactor
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service
private class RemoteKernelScopeHolder {

  suspend fun createRemoteKernel(): RemoteKernel {
    val kernelService = KernelService.instance
    val kernelScope = kernelService.kernelCoroutineScope.await()
    val kernelCoroutineContext = kernelScope.coroutineContext.kernelCoroutineContext()
    return RemoteKernelImpl(
      kernelCoroutineContext.transactor,
      kernelScope.childScope("RemoteKernelScope", kernelCoroutineContext),
      CommonInstructionSet.decoder(),
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

  override val kernelCoroutineScope: CompletableDeferred<CoroutineScope> = CompletableDeferred()

  init {
    coroutineScope.launch {
      withKernel(middleware = LeaderTransactorMiddleware(CommonInstructionSet.encoder())) {
        change {
          initWorkspaceClock()
        }
        handleEntityTypes(transactor(), this)
        kernelCoroutineScope.complete(this)
        updateDbInTheEventDispatchThread()
      }
    }
  }
}
