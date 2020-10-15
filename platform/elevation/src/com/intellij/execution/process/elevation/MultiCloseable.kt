// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

open class MultiCloseable : Closeable {
  private val cleanupList = CopyOnWriteArrayList<Closeable>()

  fun registerCloseable(closeable: Closeable) {
    cleanupList += closeable
  }

  override fun close() = close(null)

  fun close(throwable: Throwable?) {
    var suppressed = throwable
    for (closeable in cleanupList.reversed()) {
      try {
        closeable.close()
      }
      catch (e: Throwable) {
        suppressed?.let { e.addSuppressed(it) }
        suppressed = e
      }
    }
    suppressed?.let { throw it }
  }
}