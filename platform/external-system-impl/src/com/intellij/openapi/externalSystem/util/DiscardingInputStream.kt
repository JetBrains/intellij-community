// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Describes input stream which discards new data for reading on close.
 * Also, it waits for current reading completion before closing.
 */
@ApiStatus.Internal
class DiscardingInputStream(
  private val delegate: InputStream
) : InputStream() {

  private val discardData = AtomicBoolean(false)

  private val readWriteLock = ReentrantReadWriteLock()

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
    readWriteLock.write {
      discardData.set(true)
      delegate.close()
    }
  }

  private fun inputStreamReadAction(action: () -> Int): Int {
    return readWriteLock.read {
      when (discardData.get()) {
        true -> -1
        else -> action()
      }
    }
  }
}