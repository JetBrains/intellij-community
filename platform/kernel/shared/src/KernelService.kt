// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.openapi.application.ApplicationManager
import fleet.kernel.Kernel
import fleet.kernel.kernel
import kotlin.coroutines.CoroutineContext

interface KernelService {

  suspend fun coroutineContext(): CoroutineContext

  suspend fun kernel(): Kernel = coroutineContext().kernel

  companion object {

    val instance: KernelService
      get() = ApplicationManager.getApplication().getService(KernelService::class.java)

    suspend fun kernelCoroutineContext(): CoroutineContext = instance.coroutineContext()
  }
}
