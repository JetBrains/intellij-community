// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import kotlin.time.Duration.Companion.milliseconds

abstract class AutoReloadParallelTestCase: AutoReloadTestCase() {

  override fun test(
    relativePath: String,
    test: SimpleTestBench.(VirtualFile) -> Unit,
  ) = super.test(relativePath) { settingsFile ->
    Disposer.newDisposable().use { disposable ->

      AutoImportProjectTracker.enableAsyncAutoReloadInTests(disposable)
      AutoImportProjectTracker.setMergingTimeSpan(MERING_SPAN.milliseconds, disposable)
      AutoImportProjectTracker.setAutoReloadDelay(AUTO_RELOAD_DELAY.milliseconds, disposable)

      repeat(TEST_ATTEMPTS) {
        test(settingsFile)
      }
    }
  }

  companion object {
    const val MERING_SPAN = 10L //ms
    const val AUTO_RELOAD_DELAY = 100L //ms
    const val TEST_ATTEMPTS = 10
  }
}