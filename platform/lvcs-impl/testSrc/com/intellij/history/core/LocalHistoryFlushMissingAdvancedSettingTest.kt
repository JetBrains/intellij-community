// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Reproduces IJPL-248209: the background flusher reads the `localHistory.daysToKeep` advanced setting.
 *
 * Unrelated tests (e.g. SearchEverywhereMlSettingsServiceTest) call
 * [ExtensionTestUtil.maskExtensions] on [AdvancedSettingBean.EP_NAME], which wipes every advanced setting
 * except their own from the EP for the duration of the test. A leaked LocalHistory flusher ticking during
 * that window then fails to find `localHistory.daysToKeep` and throws IllegalArgumentException.
 *
 * This test reproduces that exact condition deterministically by masking the EP and invoking [ChangeListImpl.flush].
 */
@TestApplication
internal class LocalHistoryFlushMissingAdvancedSettingTest {
  @Test
  fun `flush survives when advancedSetting EP does not contain localHistory daysToKeep`() {
    val disposable = Disposer.newDisposable()
    try {
      ExtensionTestUtil.maskExtensions(AdvancedSettingBean.EP_NAME, emptyList(), disposable)

      val changeList = ChangeListImpl(InMemoryChangeListStorage())
      assertDoesNotThrow {
        runBlocking { changeList.flush() }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
