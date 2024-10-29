// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import com.google.common.net.HostAndPort
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.isPending
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.system.measureNanoTime

/**
 * Asynchronous adapter for synchronous process callers. Intended for usage in cases when blocking calls like [ProcessBuilder.start]
 * are called in EDT and it's uneasy to refactor. **Anyway, better to not call blocking methods in EDT rather than use this class.**
 *
 * **Beware!** Note that [DeferredRemoteProcess] is created even if underlying one fails to. Take care to make sure that such cases really
 * look to users like underlying process not started, not like it started and died silently (see IDEA-265188). It would help a lot with
 * future troubleshooting, reporting and investigation.
 */
class DeferredRemoteProcess(private val promise: Promise<RemoteProcess>) : RemoteProcess() {
  override fun getOutputStream(): OutputStream = DeferredOutputStream()

  override fun getInputStream(): InputStream = DeferredInputStream { it.inputStream }

  override fun getErrorStream(): InputStream = DeferredInputStream { it.errorStream }

  override fun waitFor(): Int = get().waitFor()

  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    val process: RemoteProcess?
    val nanosSpent = measureNanoTime {
      process = promise.blockingGet(timeout.toInt(), unit)
    }
    val restTimeoutNanos = max(0, TimeUnit.NANOSECONDS.convert(timeout, unit) - nanosSpent)
    return process?.waitFor(restTimeoutNanos, TimeUnit.NANOSECONDS) ?: false
  }

  override fun exitValue(): Int {
    return tryGet()?.exitValue()
           ?: throw IllegalStateException("Process is not terminated")
  }

  override fun destroy() {
    runNowOrSchedule {
      it.destroy()
    }
  }

  override fun killProcessTree(): Boolean {
    return runNowOrSchedule {
      it.killProcessTree()
    } ?: false
  }

  override fun isDisconnected(): Boolean =
    tryGet()?.isDisconnected
    ?: false

  override fun getLocalTunnel(remotePort: Int): HostAndPort? =
    get().getLocalTunnel(remotePort)

  override fun setWindowSize(columns: Int, rows: Int) {
    runNowOrSchedule {
      get().setWindowSize(columns, rows)
    }
  }

  override fun destroyForcibly(): Process =
    runNowOrSchedule {
      it.destroyForcibly()
    }
    ?: this

  override fun supportsNormalTermination(): Boolean = true

  override fun isAlive(): Boolean =
    tryGet()?.isAlive
    ?: true

  override fun onExit(): CompletableFuture<Process> {
    return CompletableFuture<Process>().also {
      promise.then(it::complete)
    }
  }

  private fun get(): RemoteProcess = promise.blockingGet(Int.MAX_VALUE)!!

  private fun tryGet(): RemoteProcess? = promise.takeUnless { it.isPending }?.blockingGet(0)

  private fun <T> runNowOrSchedule(handler: (RemoteProcess) -> T): T? {
    val process = tryGet()
    return if (process != null) {
      handler(process)
    }
    else {
      val cause = Throwable("Initially called from this context.")
      promise.then {
        try {
          it?.let(handler)
        }
        catch (err: Throwable) {
          err.addSuppressed(cause)
          LOG.info("$this: Got an error that nothing could catch: ${err.message}", err)
        }
      }
      null
    }
  }

  private inner class DeferredOutputStream : OutputStream() {
    override fun close() {
      runNowOrSchedule {
        it.outputStream.close()
      }
    }

    override fun flush() {
      tryGet()?.outputStream?.flush()
    }

    override fun write(b: Int) {
      get().outputStream.write(b)
    }

    override fun write(b: ByteArray) {
      get().outputStream.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      get().outputStream.write(b, off, len)
    }
  }

  private inner class DeferredInputStream(private val streamGetter: (RemoteProcess) -> InputStream) : InputStream() {
    override fun close() {
      runNowOrSchedule {
        streamGetter(it).close()
      }
    }

    override fun read(): Int =
      streamGetter(get()).read()

    override fun read(b: ByteArray): Int =
      streamGetter(get()).read(b)

    override fun read(b: ByteArray, off: Int, len: Int): Int =
      streamGetter(get()).read(b, off, len)

    override fun readAllBytes(): ByteArray =
      streamGetter(get()).readAllBytes()

    override fun readNBytes(len: Int): ByteArray =
      streamGetter(get()).readNBytes(len)

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int =
      streamGetter(get()).readNBytes(b, off, len)

    override fun skip(n: Long): Long =
      streamGetter(get()).skip(n)

    override fun available(): Int =
      tryGet()?.let(streamGetter)?.available() ?: 0

    override fun markSupported(): Boolean = false
  }

  private companion object {
    private val LOG = logger<DeferredRemoteProcess>()
  }
}