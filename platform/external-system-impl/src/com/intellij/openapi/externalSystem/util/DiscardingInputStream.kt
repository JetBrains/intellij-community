// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
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

  private var isDiscardData = false

  private val readWriteLock = ReentrantReadWriteLock()

  override fun read(): Int =
    inputStreamReadAction(discardResult = -1) {
      delegate.read()
    }

  override fun read(b: ByteArray): Int =
    inputStreamReadAction(discardResult = -1) {
      delegate.read(b)
    }

  override fun read(b: ByteArray, off: Int, len: Int): Int =
    inputStreamReadAction(discardResult = -1) {
      delegate.read(b, off, len)
    }

  override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int =
    inputStreamReadAction(discardResult = 0) {
      delegate.readNBytes(b, off, len)
    }

  override fun close() {
    readWriteLock.write {
      isDiscardData = true
      delegate.close()
    }
  }

  /**
   * @param discardResult is read result due to discard. Should be adjusted by 'read' contract.
   */
  private fun inputStreamReadAction(discardResult: Int, action: () -> Int): Int {
    return readWriteLock.read {
      when (isDiscardData) {
        true -> discardResult
        else -> action()
      }
    }
  }
}