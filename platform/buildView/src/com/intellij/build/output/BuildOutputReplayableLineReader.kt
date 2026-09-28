// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Extends [BuildOutputLineReader] with a push-back buffer so that parsers can re-read
 * already-consumed lines.
 *
 * Each line returned by [readLine] is recorded in a bounded history. Calling [pushBack] rewinds
 * the read position by the requested number of lines so that the next [readLine] replays them in
 * the original order. The history is capped at `pushBackBufferSize`: requesting more
 * lines than the buffer holds is silently clamped to the available depth.
 */
@Internal
interface BuildOutputReplayableLineReader : BuildOutputLineReader {

  /**
   * Rewinds the read position by [numberOfLines] so that the next [readLine] calls replay those
   * lines. Clamped to the push-back buffer size if [numberOfLines] exceeds it.
   */
  fun pushBack(numberOfLines: Int)
}
