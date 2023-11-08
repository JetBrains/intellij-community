// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase

class ModuleExtensionRegistrationTest : HeavyPlatformTestCase() {
  fun `test unregister module extension`() {
    runWithRegisteredExtension {
      assertNotNull(ModuleRootManager.getInstance(myModule).getModuleExtension(MockModuleExtension::class.java))
    }

    assertNull(ModuleRootManager.getInstance(myModule).getModuleExtension(MockModuleExtension::class.java))
  }

  private fun runWithRegisteredExtension(action: () -> Unit) {
    val moduleExtensionDisposable = Disposer.newDisposable()
    registerModuleExtension(moduleExtensionDisposable)
    try {
      action()
    }
    finally {
      Disposer.dispose(moduleExtensionDisposable)
    }
  }

  private fun registerModuleExtension(disposable: Disposable) {
    val moduleTypeDisposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      runWriteAction {
        Disposer.dispose(moduleTypeDisposable)
      }
    })
    for (module in ModuleManager.getInstance(myProject).modules) {
      @Suppress("DEPRECATION")
      ModuleRootManagerEx.MODULE_EXTENSION_NAME.getPoint(module).registerExtension(MockModuleExtension(), moduleTypeDisposable)
    }
  }
}

private class MockModuleExtension : ModuleExtension() {
  override fun getModifiableModel(writable: Boolean): ModuleExtension {
    return MockModuleExtension()
  }

  override fun commit() {
  }

  override fun isChanged(): Boolean {
    return false
  }
}