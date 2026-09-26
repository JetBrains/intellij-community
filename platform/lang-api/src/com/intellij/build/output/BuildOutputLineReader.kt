// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Accepts a stream of raw text via [notifyTextAvailable], splits it into lines, and exposes them
 * one at a time through the suspending [readLine].
 */
@Internal
interface BuildOutputLineReader {

  /**
   * Appends [text] to the internal buffer, emitting a line to [readLine] for every `\n` found.
   * Suspends if the internal channel is full (back-pressure).
   */
  suspend fun notifyTextAvailable(text: CharSequence)

  /**
   * Suspends until a line is available.
   *
   * @return the next line, or `null` when the reader is closed.
   */
  suspend fun readLine(): String?

  /**
   * Flushes any buffered partial line, signals EOF to [readLine], and releases resources.
   * Must be called exactly once; calling it a second time throws [IllegalStateException].
   */
  suspend fun close()
}
