// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

abstract class AutoReloadParallelTestCase: AutoReloadTestCase() {

  protected fun enableAsyncExecution() {
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
  }

  companion object {
    const val MERING_SPAN = 10 //ms
    const val TEST_ATTEMPTS = 10
  }
}