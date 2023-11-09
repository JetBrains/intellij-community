// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Calls [IjentChildProcess.close] after the process has exited and after both [stdout] and [stderr] are fully read.
 */
class AutoClosingIjentChildProcess private constructor(
  private val delegate: IjentChildProcess,
  override val stdout: ReceiveChannel<ByteArray>,
  override val stderr: ReceiveChannel<ByteArray>,
) : IjentChildProcess by delegate {
  companion object {
    /**
     * [parentCoroutineScope] is used for launching a coroutine that watches for the process and closes it.
     */
    @JvmStatic
    fun create(parentCoroutineScope: CoroutineScope, delegate: IjentChildProcess): AutoClosingIjentChildProcess {
      lateinit var stdout: ReceiveChannel<ByteArray>
      lateinit var stderr: ReceiveChannel<ByteArray>

      val coroutineName = "${AutoClosingIjentChildProcess::class.java.simpleName} for $delegate: watchdog"
      parentCoroutineScope
        .launch(Dispatchers.Unconfined + parentCoroutineScope.coroutineNameAppended(coroutineName)) {
          stdout = transferInScope(this, delegate.stdout)
          stderr = transferInScope(this, delegate.stderr)

          delegate.exitCode.await()
        }
        .invokeOnCompletion {
          LOG.debug { "$delegate has been fully used and is going to be destroyed now" }
          delegate.close()
        }

      return AutoClosingIjentChildProcess(delegate, stdout = stdout, stderr = stderr)
    }

    private val LOG = logger<AutoClosingIjentChildProcess>()

    /** Prevents [coroutineScope] to complete until [source] is fully read. */
    private fun transferInScope(
      coroutineScope: CoroutineScope,
      source: ReceiveChannel<ByteArray>,
    ): ReceiveChannel<ByteArray> {
      val sink = Channel<ByteArray>(capacity = Channel.RENDEZVOUS, onBufferOverflow = BufferOverflow.SUSPEND)
      coroutineScope.launch {
        while (true) {
          val tryReceive = source.tryReceive()
          if (tryReceive.isFailure) {
            val cause = tryReceive.exceptionOrNull()
            sink.close(cause)
            break
          }
          sink.send(tryReceive.getOrThrow())
        }
      }
      return sink
    }
  }

  override fun toString(): String = "${javaClass.simpleName}($delegate)"
}