// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class BuildOutputLineReaderImpl(
  linesBufferSize: Int = 64,
) : BuildOutputLineReader {

  private val channel = Channel<BuildOutputText>(linesBufferSize)

  private val lineProcessor = BuildOutputLineProcessor {
    channel.send(BuildOutputText.createOutputText(it.ids, it.text.removeSuffix("\n")))
  }

  suspend fun notifyTextAvailable(text: BuildOutputText) {
    lineProcessor.notifyTextAvailable(text)
  }

  override suspend fun readLine(): BuildOutputText? {
    return channel.receiveCatching().getOrNull()
  }

  suspend fun close() {
    lineProcessor.close()
    channel.close()
  }
}
