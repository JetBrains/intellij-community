// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BuildOutputLineParserUtil")
package com.intellij.build.output

import com.intellij.build.eventBuilders.MessageEventBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun MessageEventBuilder.withDescription(line: BuildOutputText, isSingleFlow: Boolean): MessageEventBuilder = when (isSingleFlow) {
  true -> withOutputIds(line.ids)
  else -> withDescription(line.text)
}

@Internal
fun MessageEventBuilder.withDescription(lines: List<BuildOutputText>, isSingleFlow: Boolean): MessageEventBuilder =
  when (isSingleFlow) {
    true -> withOutputIds(lines.flatMap { it.ids })
    else -> withDescription(lines.joinToString("\n") { it.text }) //NON-NLS
  }