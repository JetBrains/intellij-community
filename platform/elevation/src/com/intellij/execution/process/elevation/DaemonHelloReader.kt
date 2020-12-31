// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


internal interface DaemonHelloReader : Closeable {
  fun <R> read(doRead: (InputStream) -> R): R
  override fun close()
}

internal abstract class AbstractDaemonHelloStreamReader : DaemonHelloReader {
  abstract val inputStream: InputStream
  override fun <R> read(doRead: (InputStream) -> R): R = inputStream.let(doRead)
  override fun close() = inputStream.close()
}


internal class DaemonHelloSocketReader(port: Int = 0) : AbstractDaemonHelloStreamReader() {
  private val cleanup = MultiCloseable()

  private val serverSocket = ServerSocket(port).also(cleanup::registerCloseable)
  val port = serverSocket.localPort

  override val inputStream: InputStream by lazy { serverSocket.accept().getInputStream().also(cleanup::registerCloseable) }
  override fun close() = cleanup.close()  // don't call super.close() to avoid computing inputStream Lazy.
}

internal class DaemonHelloStdoutReader : AbstractDaemonHelloStreamReader() {
  override lateinit var inputStream: InputStream
}

internal abstract class DaemonHelloFileReader(val path: Path) : AbstractDaemonHelloStreamReader()

internal class DaemonHelloUnixFifoReader(path: Path = FileUtil.generateRandomTemporaryPath().toPath()) : DaemonHelloFileReader(path) {
  private val cleanup = MultiCloseable().apply {
    registerCloseable { super.close() }
  }

  init {
    @Suppress("SpellCheckingInspection")
    try {
      ElevationLogger.LOG.assertTrue(SystemInfo.isUnix, "Can only use 'mkfifo' on Unix")
      val mkfifoCommandLine = GeneralCommandLine("mkfifo", "-m", "0666", path.toString())
      ExecUtil.execAndGetOutput(mkfifoCommandLine)
        .checkSuccess(ElevationLogger.LOG).also { success ->
          if (success) {
            cleanup.registerCloseable { FileUtil.delete(path) }
          }
          else throw IOException("Unable to create fifo $path")
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
}
