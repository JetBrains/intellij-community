// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.rpc.backend.awaitDocumentSync
import com.intellij.platform.rpc.backend.impl.DocumentSync
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class DocumentSyncTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `returns at once without an extension`(): Unit = timeoutRunBlocking {
    awaitDocumentSync()
  }

  @Test
  fun `calls the document sync extensions`(): Unit = timeoutRunBlocking {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val extension = object : DocumentSync {
      override suspend fun awaitDocumentSync() {
        entered.complete(Unit)
        release.await()
      }
    }
    ExtensionTestUtil.addExtensions(ExtensionPointName("com.intellij.platform.rpc.backend.documentSync"), listOf(extension), disposable)

    val sync = async(start = CoroutineStart.UNDISPATCHED) { awaitDocumentSync() }
    entered.await()
    yield()
    assertThat(sync.isCompleted).isFalse()

    release.complete(Unit)
    sync.await()
  }
}
