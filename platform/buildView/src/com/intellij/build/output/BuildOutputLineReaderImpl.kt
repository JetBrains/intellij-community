// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class BuildOutputLineReaderImpl(
  linesBufferSize: Int = 64,
) : BuildOutputLineReader {

  private val channel = Channel<String>(linesBufferSize)

  private val lineProcessor = BuildOutputLineProcessor {
    channel.send(it.text)
  }

  override suspend fun notifyTextAvailable(text: CharSequence) {
    lineProcessor.notifyTextAvailable(BuildOutputText.createOutputText(text.toString()))
  }

  override suspend fun readLine(): String? {
    return channel.receiveCatching().getOrNull()
  }

  override suspend fun close() {
    lineProcessor.close()
    channel.close()
  }
}
