// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.traceRun
import com.intellij.openapi.observable.operation.core.waitForOperationCompletion
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Describes input stream which discards new data for reading on close.
 * Also, it waits for current reading completion before closing.
 */
@ApiStatus.Internal
class DiscardingInputStream(
  private val delegate: InputStream,
  private val timeout: Duration,
) : InputStream() {

  constructor(delegate: InputStream, timeout: Long, timeUnit: TimeUnit) :
    this(delegate, TimeUnit.MILLISECONDS.convert(timeout, timeUnit).milliseconds)

  private val discardData = AtomicBoolean(false)
  private val readOperation = AtomicOperationTrace()

  override fun read(): Int =
    inputStreamReadAction {
      delegate.read()
    }

  override fun read(b: ByteArray): Int =
    inputStreamReadAction {
      delegate.read(b)
    }

  override fun read(b: ByteArray, off: Int, len: Int): Int =
    inputStreamReadAction {
      delegate.read(b, off, len)
    }

  override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int =
    inputStreamReadAction {
      delegate.readNBytes(b, off, len)
    }

  override fun close() {
    delegate.close()
  }

  @Throws(IOException::class)
  fun discardAndClose() {
    delegate.use {
      discardData.set(true)
      readOperation.waitForOperationCompletion(timeout)
    }
  }

  private fun inputStreamReadAction(action: () -> Int): Int {
    return readOperation.traceRun {
      when (discardData.get()) {
        true -> -1
        else -> action()
      }
    }
  }
}