// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.process.mediator.rpc.DaemonHello
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.*
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
  fun consumeDaemonHello(): DaemonHello
}

internal abstract class AbstractInputStreamDaemonHelloIpc(
  coroutineScope: CoroutineScope
) : DaemonHelloIpc, ProcessAdapter(),
    CoroutineScope by coroutineScope.childScope() {

  private val cleanupList = CopyOnWriteArrayList<Closeable>()

  init {
    // TODO[eldar] revise the usage of invokeOnCompletion():
    //   **Note**: Implementation of `CompletionHandler` must be fast, non-blocking, and thread-safe.
    //   This handler can be invoked concurrently with the surrounding code.
    //   There is no guarantee on the execution context in which the [handler] is invoked.
    @Suppress("LeakingThis")
    coroutineContext[Job]!!.invokeOnCompletion { close() }
  }

  final override fun createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return createProcessHandler(daemonCommandLine).also {
      it.addProcessListener(this)
    }
  }

  protected open fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return OSProcessHandler.Silent(daemonCommandLine)
  }

  override fun consumeDaemonHello(): DaemonHello {
    return try {
      ensureActive()
      openInputStream().use { inputStream ->
        ensureActive()
        DaemonHello.parseDelimitedFrom(inputStream)
      }
    }
    catch (e: CancellationException) {
      throw IOException(e)
    }
  }

  protected abstract fun openInputStream(): InputStream

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    ElevationLogger.LOG.info("Daemon [$outputType]: ${event.text}")
  }

  override fun processTerminated(event: ProcessEvent) {
    val message = "Daemon process terminated with exit code ${event.exitCode}"
    ElevationLogger.LOG.info(message)
    cancel(message)
    close()
  }

  protected fun registerCloseable(closeable: Closeable) {
    cleanupList += closeable
  }

  override fun close() {
    try {
      var exception: Exception? = null
      for (closeable in cleanupList) {
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
    finally {
      cancel("Closed")
    }
  }
}

internal open class ProcessOutputBasedDaemonHelloIpc(coroutineScope: CoroutineScope) : AbstractInputStreamDaemonHelloIpc(coroutineScope),
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

  override fun openInputStream() = stdoutStream
}

internal open class FileBasedDaemonHelloIpc(coroutineScope: CoroutineScope,
                                            protected val path: Path) : AbstractInputStreamDaemonHelloIpc(coroutineScope) {
  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-file", path.toAbsolutePath().toString())
  }

  override fun openInputStream(): InputStream = Files.newInputStream(path)
}

internal class UnixFifoDaemonHelloIpc private constructor(coroutineScope: CoroutineScope,
                                                          path: Path) : FileBasedDaemonHelloIpc(coroutineScope, path) {
  init {
    // We need to open the write end of the pipe to ensure opening the pipe for reading would not block indefinitely
    // in case the real daemon process doesn't open in for writing (for example, if it fails to start)
    Files.newByteChannel(path, READ, WRITE).also(::registerCloseable)
  }

  override fun openInputStream(): InputStream = Files.newInputStream(path)

  companion object {
    fun create(coroutineScope: CoroutineScope): UnixFifoDaemonHelloIpc {
      val path = FileUtil.generateRandomTemporaryPath().toPath()

      @Suppress("SpellCheckingInspection")
      val mkfifoCommandLine = GeneralCommandLine("mkfifo", "-m", "0666", path.toString())
      ExecUtil.execAndGetOutput(mkfifoCommandLine)
        .checkSuccess(ElevationLogger.LOG).also { success ->
          if (!success) throw IOException("Unable to create fifo $path")
        }
      return UnixFifoDaemonHelloIpc(coroutineScope, path).also {
        it.registerCloseable {
          FileUtil.delete(path)
        }
      }
    }
  }
}

internal class SocketBasedDaemonHelloIpc(coroutineScope: CoroutineScope,
                                         serverSocketPort: Int = 0) : AbstractInputStreamDaemonHelloIpc(coroutineScope) {
  private val serverSocket = ServerSocket(serverSocketPort).also(::registerCloseable)

  private val socketDeferred = async(Dispatchers.IO) {
    serverSocket.accept().also(::registerCloseable)
  }

  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-port", serverSocket.localPort.toString())
  }

  override fun openInputStream(): InputStream = runBlocking(coroutineContext) {
    socketDeferred.await().getInputStream()
  }
}

private fun CoroutineScope.childScope(): CoroutineScope = this + childJob()
private fun CoroutineScope.childJob(): CompletableJob = Job(coroutineContext[Job]!!)
