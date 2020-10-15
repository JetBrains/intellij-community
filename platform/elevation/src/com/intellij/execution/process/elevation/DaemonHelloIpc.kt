// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.process.mediator.rpc.DaemonHello
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CopyOnWriteArrayList

internal interface DaemonHelloIpc : Closeable {
  fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine): GeneralCommandLine
  fun createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler

  /**
   * Blocks until the greeting message from the daemon process. Returns null if the stream has reached EOF prematurely.
   */
  @Throws(IOException::class)
  fun readHello(): DaemonHello?

  /**
   * The contract is that invoking close() interrupts any ongoing blocking operations,
   * and makes [readHello] throw. The implementations must guarantee that.
   */
  override fun close()
}

internal abstract class AbstractInputStreamDaemonHelloIpc : DaemonHelloIpc, ProcessAdapter() {

  private val cleanupList = CopyOnWriteArrayList<Closeable>()

  final override fun createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return createProcessHandler(daemonCommandLine).also {
      it.addProcessListener(this)
    }
  }

  protected open fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return OSProcessHandler.Silent(daemonCommandLine)
  }

  override fun readHello(): DaemonHello? {
    return readAndClose(DaemonHello::parseDelimitedFrom).also {
      if (it?.port == 0) {
        throw IOException("Invalid/incomplete hello message from daemon: $it")
      }
    }
  }

  protected abstract fun <R> readAndClose(doRead: (InputStream) -> R): R

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    ElevationLogger.LOG.info("Daemon [$outputType]: ${event.text}")
  }

  override fun processTerminated(event: ProcessEvent) {
    ElevationLogger.LOG.info("Daemon process terminated with exit code ${event.exitCode}")
    close()
  }

  protected fun registerCloseable(closeable: Closeable) {
    cleanupList += closeable
  }

  override fun close() {
    var exception: Exception? = null
    for (closeable in cleanupList.reversed()) {
      try {
        closeable.close()
      }
      catch (e: Exception) {
        exception?.let { e.addSuppressed(it) }
        exception = e
      }
    }
    exception?.let { throw it }
  }
}

internal open class ProcessOutputBasedDaemonHelloIpc : AbstractInputStreamDaemonHelloIpc(),
                                                       ProcessListener {
  private lateinit var stdoutStream: InputStream

  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-file", "-")
  }

  override fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return object : OSProcessHandler.Silent(daemonCommandLine) {
      override fun createProcessOutReader(): Reader {
        return Reader.nullReader()
      }
    }.also {
      stdoutStream = it.process.inputStream
    }
  }

  override fun <R> readAndClose(doRead: (InputStream) -> R): R = stdoutStream.use(doRead)
}

internal open class FileBasedDaemonHelloIpc(protected val path: Path) : AbstractInputStreamDaemonHelloIpc() {
  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-file", path.toAbsolutePath().toString())
  }

  override fun <R> readAndClose(doRead: (InputStream) -> R): R = Files.newInputStream(path).use(doRead)
}

internal class UnixFifoDaemonHelloIpc(path: Path = FileUtil.generateRandomTemporaryPath().toPath()) : FileBasedDaemonHelloIpc(path) {
  init {
    @Suppress("SpellCheckingInspection")
    try {
      ElevationLogger.LOG.assertTrue(SystemInfo.isUnix, "Can only use 'mkfifo' on Unix")
      val mkfifoCommandLine = GeneralCommandLine("mkfifo", "-m", "0666", path.toString())
      ExecUtil.execAndGetOutput(mkfifoCommandLine)
        .checkSuccess(ElevationLogger.LOG).also { success ->
          if (success) {
            registerCloseable { FileUtil.delete(path) }
          }
          else throw IOException("Unable to create fifo $path")
        }

      // We need to open the write end of the pipe  to ensure opening the pipe for reading would not block indefinitely
      // in case the real daemon process doesn't open in for writing (for example, if it fails to start)
      Files.newByteChannel(path, READ, WRITE).also(::registerCloseable)
    }
    catch (e: Throwable) {
      use {  // to close any closeable registered so far
        throw e
      }
    }
  }

  private val inputStream = Files.newInputStream(path, READ).also(::registerCloseable)

  override fun <R> readAndClose(doRead: (InputStream) -> R): R = inputStream.use(doRead)
}

internal class SocketBasedDaemonHelloIpc(serverSocketPort: Int = 0) : AbstractInputStreamDaemonHelloIpc() {
  private val serverSocket = ServerSocket(serverSocketPort).also(::registerCloseable)

  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-port", serverSocket.localPort.toString())
  }

  override fun <R> readAndClose(doRead: (InputStream) -> R): R {
    return serverSocket.accept().use { socket ->
      socket.getInputStream().use(doRead)
    }
  }
}
