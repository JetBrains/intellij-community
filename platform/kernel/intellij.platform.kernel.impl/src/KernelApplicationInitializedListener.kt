// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.impl

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.kernel.KernelService

/**
 * Suspend initializers are not supported.
 * This listener effectively preloads a service and awaits the completion of the initialization routine.
 */
internal class KernelApplicationInitializedListener : ApplicationInitializedListener {

  override suspend fun execute() {
    val service = ApplicationManager.getApplication().serviceAsync<KernelService>()
    service.kernelCoroutineScope.join()
  }
}
