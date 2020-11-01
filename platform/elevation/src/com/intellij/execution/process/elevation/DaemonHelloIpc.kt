// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.daemon.rsaDecrypt
import com.intellij.execution.process.mediator.rpc.DaemonHello
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.BaseInputStreamReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator

internal interface DaemonHelloIpc : Closeable {
  fun getDaemonLaunchOptions(): DaemonLaunchOptions
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

internal class EncryptedDaemonHelloIpc(private val delegate: DaemonHelloIpc) : DaemonHelloIpc by delegate {
  private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
    initialize(1024)
  }.genKeyPair()

  override fun getDaemonLaunchOptions(): DaemonLaunchOptions {
    return delegate.getDaemonLaunchOptions().run {
      check(tokenEncryptionOption == null) { "Already encrypted" }
      copy(tokenEncryptionOption = DaemonLaunchOptions.TokenEncryptionOption(keyPair.public))
    }
  }

  override fun readHello(): DaemonHello? {
    return delegate.readHello()?.let { daemonHello ->
      daemonHello.toBuilder().apply {
        token = keyPair.private.rsaDecrypt(token)
      }.build()
    }
  }
}

internal fun DaemonHelloIpc.encrypted(): DaemonHelloIpc = EncryptedDaemonHelloIpc(this)


internal abstract class AbstractDaemonHelloIpcBase<R : DaemonHelloReader> : DaemonHelloIpc, ProcessAdapter() {
  protected abstract val helloReader: R

  override fun getDaemonLaunchOptions() = DaemonLaunchOptions(helloOption = getHelloOption())
  abstract fun getHelloOption(): DaemonLaunchOptions.HelloOption

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
    val exitCodeString = ProcessTerminatedListener.stringifyExitCode(event.exitCode)
    ElevationLogger.LOG.info("Daemon process terminated with exit code ${exitCodeString}")
    close()
  }

  override fun close() = helloReader.close()
}

internal abstract class AbstractDaemonHelloIpc<R : DaemonHelloReader>(override val helloReader: R) : AbstractDaemonHelloIpcBase<R>()

internal class DaemonHelloSocketIpc(
  port: Int = 0
) : AbstractDaemonHelloIpc<DaemonHelloSocketReader>(DaemonHelloSocketReader(port)) {
  override fun getHelloOption() = DaemonLaunchOptions.HelloOption.Port(helloReader.port)
}

internal class DaemonHelloStdoutIpc : AbstractDaemonHelloIpcBase<DaemonHelloStreamReader>() {
  override lateinit var helloReader: DaemonHelloStreamReader

  override fun getHelloOption() = DaemonLaunchOptions.HelloOption.Stdout

  override fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return object : OSProcessHandler.Silent(daemonCommandLine) {
      override fun createProcessOutReader(): Reader {
        return BaseInputStreamReader(InputStream.nullInputStream())  // don't let the process handler touch the stdout stream
      }
    }.also {
      helloReader = DaemonHelloStreamReader(it.process.inputStream)
    }
  }
}

internal open class DaemonHelloFileIpc(
  helloReader: DaemonHelloFileReader
) : AbstractDaemonHelloIpc<DaemonHelloFileReader>(helloReader) {
  override fun getHelloOption() = DaemonLaunchOptions.HelloOption.File(helloReader.path.toAbsolutePath())
}

internal class DaemonHelloUnixFifoIpc(
  path: Path = FileUtil.generateRandomTemporaryPath().toPath()
) : DaemonHelloFileIpc(DaemonHelloUnixFifoReader(path))

