// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.StartId
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * Parses output messages and creates build events from those.
 */
@Internal
@OverrideOnly
interface BuildOutputLineParser {

  /**
   * Parses output line.
   *
   * @param line        is line to parse,
   * @param reader      is source reader of [line],
   * @param taskId      is parent id to attach parsed build events,
   * @param multicaster is consumer of parsed build events.
   * @return `true` if [line] is successfully parsed
   */
  suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
  ): Boolean
}
