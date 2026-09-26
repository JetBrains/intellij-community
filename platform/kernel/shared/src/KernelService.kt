// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

/**
 * Don't use directly. Use [withKernel] instead.
 */
interface KernelService {

  val kernelCoroutineScope: Deferred<CoroutineScope>

  companion object {

    val instance: KernelService
      get() = ApplicationManager.getApplication().getService(KernelService::class.java)
  }
}
