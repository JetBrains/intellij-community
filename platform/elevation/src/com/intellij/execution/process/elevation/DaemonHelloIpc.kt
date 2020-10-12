// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.google.protobuf.ByteString
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.mediator.ChannelInputStream
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.sendBlocking
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CopyOnWriteArrayList

data class DaemonHello(val port: Int) {
  companion object {
    fun parseLines(lineSequence: Sequence<String>): DaemonHello {
      val lines = lineSequence.take(1).toList()
      return parseLines(lines)
    }

    private fun parseLines(lines: List<String>): DaemonHello {
      return try {
        val port = lines.first().toInt()
        DaemonHello(port)
      }
      catch (e: Exception) {
        when (e) {
          is NoSuchElementException,
          is NumberFormatException -> throw IOException("Unable to read daemon hello", e)
          else -> throw e
        }
      }
    }
  }
}

internal interface DaemonHelloIpc : ProcessListener, Closeable {
  fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine): GeneralCommandLine
  fun consumeDaemonHello(): DaemonHello
}

internal abstract class AbstractInputStreamDaemonHelloIpc(coroutineScope: CoroutineScope) : DaemonHelloIpc, ProcessAdapter(),
                                                                                            CoroutineScope by coroutineScope.childScope() {
  private val cleanupList = CopyOnWriteArrayList<Closeable>()

  protected abstract fun openInputStream(): InputStream

  override fun consumeDaemonHello(): DaemonHello {
    return try {
      ensureActive()
      openInputStream().reader(Charsets.UTF_8).buffered().use { reader ->
        ensureActive()
        DaemonHello.parseLines(reader.lineSequence())
      }
    }
    catch (e: CancellationException) {
      throw IOException(e)
    }
  }

  override fun processTerminated(event: ProcessEvent) {
    cancel("Process terminated with exit code ${event.exitCode}")
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
  private val stdoutChannel = Channel<ByteString>(capacity = Channel.BUFFERED)

  override fun openInputStream() = ChannelInputStream(stdoutChannel)

  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-file", "-")
  }

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    if (outputType == ProcessOutputType.STDOUT) {
      // Don't wanna rely on whether it is always complete lines or not, also it's easier to reuse ChannelInputStream
      val byteString = ByteString.copyFrom(event.text, Charsets.UTF_8)
      try {
        stdoutChannel.sendBlocking(byteString)
      }
      catch (e: ClosedSendChannelException) {
        event.processHandler.removeProcessListener(this)
      }
    }
  }

  override fun startNotified(event: ProcessEvent) = Unit

  override fun close() {
    try {
      stdoutChannel.close()
    }
    finally {
      super.close()
    }
  }
}

internal open class FileBasedDaemonHelloIpc(coroutineScope: CoroutineScope,
                                            private val path: Path) : AbstractInputStreamDaemonHelloIpc(coroutineScope) {
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
    async(Dispatchers.IO) {
      Files.newByteChannel(path, READ, WRITE).also(::registerCloseable)
    }
  }

  companion object {
    fun create(coroutineScope: CoroutineScope): UnixFifoDaemonHelloIpc {
      val path = FileUtil.generateRandomTemporaryPath().toPath()

      @Suppress("SpellCheckingInspection")
      val mkfifoCommandLine = GeneralCommandLine("mkfifo", "-m", "0666", path.toString())
      ExecUtil.execAndGetOutput(mkfifoCommandLine)
        .checkSuccess(ElevationLogger.LOG).also { success ->
          if (!success) throw IOException("Unable to create fifo $path")
        }
      return UnixFifoDaemonHelloIpc(coroutineScope, path)
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
