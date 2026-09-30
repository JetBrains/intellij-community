// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Accepts a stream of raw text, splits it into lines, and exposes them one at a time through the [readLine].
 */
@Internal
interface BuildOutputLineReader {

  /**
   * Suspends until a line is available.
   *
   * @return the next line, or `null` when the reader is closed.
   */
  suspend fun readLine(): BuildOutputText?
}
