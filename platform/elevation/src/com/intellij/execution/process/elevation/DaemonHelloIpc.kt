// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.mediator.rpc.DaemonHello
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import java.io.Closeable
import java.io.IOException
import java.io.Reader
import java.nio.file.Path

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


internal abstract class AbstractDaemonHelloIpc<R : DaemonHelloReader>(protected val helloReader: R) : DaemonHelloIpc, ProcessAdapter() {

  final override fun createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return createProcessHandler(daemonCommandLine).also {
      it.addProcessListener(this)
    }
  }

  protected open fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return OSProcessHandler.Silent(daemonCommandLine)
  }

  override fun readHello(): DaemonHello? {
    return helloReader.read(DaemonHello::parseDelimitedFrom).also {
      if (it?.port == 0) {
        throw IOException("Invalid/incomplete hello message from daemon: $it")
      }
    }
  }

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    ElevationLogger.LOG.info("Daemon [$outputType]: ${event.text}")
  }

  override fun processTerminated(event: ProcessEvent) {
    ElevationLogger.LOG.info("Daemon process terminated with exit code ${event.exitCode}")
    close()
  }

  override fun close() = helloReader.close()
}

internal class DaemonHelloSocketIpc(
  port: Int = 0
) : AbstractDaemonHelloIpc<DaemonHelloSocketReader>(DaemonHelloSocketReader(port)) {
  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-port", helloReader.port.toString())
  }
}

internal class DaemonHelloStdoutIpc : AbstractDaemonHelloIpc<DaemonHelloStdoutReader>(DaemonHelloStdoutReader()) {
  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-file", "-")
  }

  override fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return object : OSProcessHandler.Silent(daemonCommandLine) {
      override fun createProcessOutReader(): Reader {
        return Reader.nullReader()  // don't let the process handler touch the stdout stream
      }
    }.also {
      helloReader.inputStream = it.process.inputStream
    }
  }
}

internal open class DaemonHelloFileIpc(
  helloReader: DaemonHelloFileReader
) : AbstractDaemonHelloIpc<DaemonHelloFileReader>(helloReader) {
  override fun patchDaemonCommandLine(daemonCommandLine: GeneralCommandLine) = daemonCommandLine.apply {
    addParameters("--hello-file", helloReader.path.toAbsolutePath().toString())
  }
}

internal class DaemonHelloUnixFifoIpc(
  path: Path = FileUtil.generateRandomTemporaryPath().toPath()
) : DaemonHelloFileIpc(DaemonHelloUnixFifoReader(path))

