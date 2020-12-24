// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.handshake

import com.intellij.util.io.MultiCloseable
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit


internal interface HandshakeReader : Closeable {
  fun <R> read(doRead: (InputStream) -> R): R
  override fun close()
}

internal abstract class AbstractHandshakeStreamReader : HandshakeReader {
  abstract val inputStream: InputStream
  override fun <R> read(doRead: (InputStream) -> R): R = inputStream.let(doRead)
  override fun close() = inputStream.close()
}


internal class HandshakeSocketReader(port: Int = 0) : AbstractHandshakeStreamReader() {
  private val cleanup = MultiCloseable()

  private val serverSocket = ServerSocket(port).also(cleanup::registerCloseable)

  @Suppress("EXPERIMENTAL_API_USAGE")
  val port = serverSocket.localPort.toUShort()

  override val inputStream: InputStream by lazy { serverSocket.accept().getInputStream().also(cleanup::registerCloseable) }
  override fun close() = cleanup.close()  // don't call super.close() to avoid computing inputStream Lazy.
}

internal class HandshakeStreamReader(override val inputStream: InputStream) : AbstractHandshakeStreamReader()

internal abstract class HandshakeFileReader(val path: Path) : AbstractHandshakeStreamReader()

@Suppress("SpellCheckingInspection")
internal class HandshakeUnixFifoReader(path: Path) : HandshakeFileReader(path) {
  private val cleanup = MultiCloseable().apply {
    registerCloseable { super.close() }
  }

  init {
    try {
      mkfifo(path).also {
        cleanup.registerCloseable { Files.delete(path) }
      }

      // We need to open the write end of the pipe to ensure opening the pipe for reading would not block indefinitely
      // in case the real daemon process doesn't open in for writing (for example, if it fails to start).
      Files.newByteChannel(path, StandardOpenOption.READ, StandardOpenOption.WRITE).also(cleanup::registerCloseable)
    }
    catch (e: Throwable) {
      use {  // to close any closeable registered so far
        throw e
      }
    }
  }

  override val inputStream: InputStream = Files.newInputStream(path, StandardOpenOption.READ)

  override fun close() = cleanup.close()

  companion object {
    private const val MKFIFO_PROCESS_TIMEOUT_MS = 3000L

    private fun mkfifo(path: Path) {
      val process = ProcessBuilder("mkfifo", "-m", "0666", path.toString()).start()
      try {
        val completed = try {
          Thread.interrupted() // clear
          process.waitFor(MKFIFO_PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        catch (e: InterruptedException) {
          throw IOException("mkfifo interrupted", e)
        }
        if (!completed) {
          throw IOException("mkfifo timed out ($MKFIFO_PROCESS_TIMEOUT_MS ms)")
        }
        if (process.exitValue() != 0) {
          throw IOException("mkfifo failed with exit code ${process.exitValue()}: ${String(process.errorStream.readAllBytes())}")
        }
      }
      catch (e: Throwable) {
        process.destroyForcibly().onExit().whenComplete { _, _ ->
          Files.deleteIfExists(path)
        }
        throw e
      }
    }
  }
}
