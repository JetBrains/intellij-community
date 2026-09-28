// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Accepts raw build output text and dispatches it to [BuildOutputParser]s.
 *
 * The dispatcher splits text fed via [notifyTextAvailable] into lines and routes each
 * non-blank line through the registered parsers on a background coroutine.
 * The [notifyTextAvailable] and [close] functions are suspend that apply back-pressure when
 * the internal buffer is full and await completion when the reader finishes.
 *
 * Parsers themselves use the synchronous [BuildOutputInstantReader] API, which is bridged inside
 * the implementation via a thin `runBlocking` wrapper.
 *
 * @see BuildOutputParser
 * @see BuildOutputInstantReader
 * @see BuildOutputLineReader
 * @see BuildOutputReplayableLineReader
 */
@Internal
interface BuildOutputParserDispatcher {

  /**
   * Forwards [text] to the underlying line reader. Suspends if the internal buffer is full.
   */
  suspend fun notifyTextAvailable(text: CharSequence)

  /**
   * Closes the underlying line reader and suspends until all buffered lines have been dispatched to parsers.
   */
  suspend fun close()
}
