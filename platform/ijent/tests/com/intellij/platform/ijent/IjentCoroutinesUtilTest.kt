// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelUnavailableException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Tests for [throwIfInsideIjentFsBlocking] (IJPL-245001): an IJent deployment triggered from inside
 * an fsBlocking call must fail fast, because the deployment may need a round trip to EDT (e.g. an SSH
 * authentication dialog) while fsBlocking blocks EDT itself or holds the read lock.
 * The error is an [IOException], so nio callers handle it like an unreachable environment.
 */
class IjentCoroutinesUtilTest {
  @Test
  fun `throws when the caller blocks EDT`(): Unit = runBlocking {
    val element = IjentCallerContextElement(IjentCallerContext(isRead = false, isWrite = false, isDispatchThread = true, reconnectUi = MockReconnectUiHandle))
    withContext(element) {
      val err = shouldThrow<EelUnavailableException.CommunicationFailure> {
        throwIfInsideIjentFsBlocking()
      }
      err.message shouldContain "IJPL-245001"
      err.shouldBeInstanceOf<IOException>()
    }
  }

  @Test
  fun `throws when the caller is a background thread`(): Unit = runBlocking {
    val element = IjentCallerContextElement(IjentCallerContext(isRead = true, isWrite = false, isDispatchThread = false, reconnectUi = MockReconnectUiHandle))
    withContext(element) {
      shouldThrow<EelUnavailableException.CommunicationFailure> {
        throwIfInsideIjentFsBlocking()
      }
    }
  }

  @Test
  fun `does not throw outside of fsBlocking`(): Unit = runBlocking {
    shouldNotThrowAny {
      throwIfInsideIjentFsBlocking()
    }
  }
}

object MockReconnectUiHandle : ReconnectUiHandle {
  override suspend fun <T> withRequestedDialog(f: suspend (dialog: ReconnectUiDialog) -> T): T {
    throw UnsupportedOperationException()
  }
}
