// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.grpc

import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal class ExceptionAsStatusTest {
  @Test
  fun `wraps known exceptions`() {
    assertThrows<StatusException> { ExceptionAsStatus.wrap { throw IOException("IOE") } }
      .also { assertEquals(Status.Code.NOT_FOUND, it.status.code, it.toString()) }
    assertThrows<StatusException> { ExceptionAsStatus.wrap { throw FileNotFoundException("FNFE") } }
      .also { assertEquals(Status.Code.NOT_FOUND, it.status.code, it.toString()) }
    assertThrows<StatusException> { ExceptionAsStatus.wrap { throw object : FileNotFoundException("my FNFE") {} } }
      .also { assertEquals(Status.Code.NOT_FOUND, it.status.code, it.toString()) }
    assertThrows<StatusException> { ExceptionAsStatus.wrap { assert(false) } }
      .also { assertEquals(Status.Code.DATA_LOSS, it.status.code, it.toString()) }
    assertThrows<StatusException> { ExceptionAsStatus.wrap { throw QuotaExceededException() } }
      .also { assertEquals(Status.Code.RESOURCE_EXHAUSTED, it.status.code, it.toString()) }
  }

  @Test
  fun `doesn't wrap status exceptions`() {
    val statusException = Status.ALREADY_EXISTS.asException()
    assertThrows<StatusException> { ExceptionAsStatus.wrap { throw statusException } }
      .also { assertTrue(it === statusException, it.toString()) }

    val statusRuntimeException = Status.ALREADY_EXISTS.asRuntimeException()
    assertThrows<StatusRuntimeException> { ExceptionAsStatus.wrap { throw statusRuntimeException } }
      .also { assertTrue(it === statusRuntimeException, it.toString()) }
  }

  @Test
  fun `unwraps wrapped exceptions`() {
    checkWrapAndUnwrap(IOException("IOE: ioe..."))
    checkWrapAndUnwrap(FileNotFoundException("FNFE: not found..."))
    checkWrapAndUnwrap(CancellationException("CE: cancel"))
    checkWrapAndUnwrap(QuotaExceededException())
  }

  @Test
  fun `unwraps known wrapped exceptions constructed with no message`() {
    for (constructor in getAllKnownConstructors()) {
      checkWrapAndUnwrap(constructor(null, null))
    }
  }

  @Test
  fun `unwraps known wrapped exceptions constructed with message`() {
    for (constructor in getAllKnownConstructors()) {
      checkWrapAndUnwrap(constructor("error message", null))
    }
  }

  @Test
  fun `unwraps known wrapped exceptions constructed with message and cause`() {
    for (constructor in getAllKnownConstructors()) {
      checkWrapAndUnwrap(constructor("error message", Exception("cause")))
    }
  }

  private fun getAllKnownConstructors(): List<(String?, Throwable?) -> Throwable> {
    return ExceptionAsStatus.KNOWN_EXCEPTIONS.values.map { it.exceptionDescriptor.constructor }
  }

  private inline fun <reified T : Throwable> checkWrapAndUnwrap(throwable: T) {
    assertThrows<T> {
      ExceptionAsStatus.unwrap {
        ExceptionAsStatus.wrap {
          throw throwable
        }
      }
    }.also {
      assertEquals(throwable.message, it.message)
    }
  }
}