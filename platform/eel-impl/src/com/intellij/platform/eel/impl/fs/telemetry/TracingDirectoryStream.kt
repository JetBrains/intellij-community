// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import java.nio.file.DirectoryStream

internal class TracingDirectoryStream<T>(
  private val delegate: DirectoryStream<T>,
  private val spanNamePrefix: String
) : DirectoryStream<T> {
  override fun close() {
    Measurer.measure(Measurer.Operation.directoryStreamClose, spanNamePrefix) {
      delegate.close()
    }
  }

  override fun iterator(): MutableIterator<T> =
    object : MutableIterator<T> {
      private val iterator = delegate.iterator()

      override fun hasNext(): Boolean =
        Measurer.measure(Measurer.Operation.directoryStreamIteratorNext, spanNamePrefix) {
          iterator.hasNext()
        }

      override fun next(): T =
        Measurer.measure(Measurer.Operation.directoryStreamIteratorNext, spanNamePrefix) {
          iterator.next()
        }

      override fun remove() {
        Measurer.measure(Measurer.Operation.directoryStreamIteratorRemove, spanNamePrefix) {
          iterator.remove()
        }
      }
    }
}
