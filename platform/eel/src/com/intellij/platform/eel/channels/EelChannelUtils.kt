// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * @param context he reader performs a blocking `read()` that parks its thread for the whole lifetime of the channel.
 *  Callers that must keep that thread off `Dispatchers.IO` (e.g. IJent, which has to stay on its own
 *  whitelisted thread pool) can override this context. See `IjentThreadPool` for the rationale.
 */
@ApiStatus.Experimental
suspend fun <T> EelReceiveChannel.useLines(context: CoroutineContext = Dispatchers.IO, body: suspend (Channel<String>) -> T): T {
  return coroutineScope {
    val channel = Channel<String>()

    // TODO Optimize later to read asynchronously.
    launch(context) {
      try {
        consumeAsInputStream(context).reader().useLines { lines ->
          for (line in lines) {
            channel.send(line)
          }
        }
      }
      finally {
        channel.close()
      }
    }

    body(channel)
  }
}