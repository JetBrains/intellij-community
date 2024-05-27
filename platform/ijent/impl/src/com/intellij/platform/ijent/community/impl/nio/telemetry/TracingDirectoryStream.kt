// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.telemetry

import com.intellij.platform.ijent.community.impl.nio.telemetry.Measurer.Operation.*
import java.nio.file.DirectoryStream

internal class TracingDirectoryStream<T>(
  private val delegate: DirectoryStream<T>,
) : DirectoryStream<T> {
  override fun close() {
    Measurer.measure(directoryStreamClose) {
      delegate.close()
    }
  }

  override fun iterator(): MutableIterator<T> =
    object : MutableIterator<T> {
      private val iterator = delegate.iterator()

      override fun hasNext(): Boolean =
        Measurer.measure(directoryStreamIteratorNext) {
          iterator.hasNext()
        }

      override fun next(): T =
        Measurer.measure(directoryStreamIteratorNext) {
          iterator.next()
        }

      override fun remove() {
        Measurer.measure(directoryStreamIteratorRemove) {
          iterator.remove()
        }
      }
    }
}
