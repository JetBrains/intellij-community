// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.ide.rpc.DocumentSync
import com.intellij.ide.rpc.awaitDocumentSync
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    assertWaitsForExtension { awaitDocumentSync() }
  }

  @Test
  @Suppress("DEPRECATION")
  fun `deprecated rpc backend function calls the document sync extensions`(): Unit = timeoutRunBlocking {
    assertWaitsForExtension { com.intellij.platform.rpc.backend.awaitDocumentSync() }
  }

  private suspend fun assertWaitsForExtension(sync: suspend () -> Unit) {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val extension = object : DocumentSync {
      override suspend fun awaitDocumentSync() {
        entered.complete(Unit)
        release.await()
      }
    }
    ExtensionTestUtil.addExtensions(ExtensionPointName("com.intellij.platform.rpc.backend.documentSync"), listOf(extension), disposable)

    coroutineScope {
      val call = async(start = CoroutineStart.UNDISPATCHED) { sync() }
      entered.await()
      yield()
      assertThat(call.isCompleted).isFalse()

      release.complete(Unit)
      call.await()
    }
  }
}
