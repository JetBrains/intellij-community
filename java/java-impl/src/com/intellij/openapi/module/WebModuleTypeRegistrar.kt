// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Registers [WebModuleType] in IDEs which don't register neither [WebModuleType] nor [com.intellij.webcore.moduleType.PlatformWebModuleType]
 * but still need to use [WebModuleTypeBase] because they have Java plugin installed.
 */
private class WebModuleTypeRegistrar(coroutineScope: CoroutineScope) : Disposable {
  private val webModuleTypeRef = AtomicReference<WebModuleType>()

  init {
    coroutineScope.launch {
      val moduleTypeManager = serviceAsync<ModuleTypeManager>()
      if (moduleTypeManager.findByID(WebModuleTypeBase.WEB_MODULE) is UnknownModuleType) {
        val webModuleType = WebModuleType()
        moduleTypeManager.registerModuleType(webModuleType)
        webModuleTypeRef.set(webModuleType)
      }
    }
  }

  override fun dispose() {
    webModuleTypeRef.getAndSet(null)?.let {
      serviceIfCreated<ModuleTypeManager>()?.unregisterModuleType(it)
    }
  }
}
