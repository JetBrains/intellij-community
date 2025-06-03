// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.Disposable

/**
 * Registers [WebModuleType] in IDEs which don't register neither [WebModuleType] nor [com.intellij.webcore.moduleType.PlatformWebModuleType]
 * but still need to use [WebModuleTypeBase] because they have Java plugin installed.
 */
internal class WebModuleTypeRegistrar : Disposable {
  private val webModuleType: WebModuleType?

  init {
    val moduleTypeManager = ModuleTypeManager.getInstance()
    if (moduleTypeManager.findByID(WebModuleTypeBase.WEB_MODULE) is UnknownModuleType) {
      webModuleType = WebModuleType()
      moduleTypeManager.registerModuleType(webModuleType)
    }
    else {
      webModuleType = null
    }
  }

  override fun dispose() {
    if (webModuleType != null) {
      ModuleTypeManager.getInstance().unregisterModuleType(webModuleType)
    }
  }
}
