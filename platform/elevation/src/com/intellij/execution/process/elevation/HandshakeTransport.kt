// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.process.mediator.util.rsaDecrypt
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

internal interface HandshakeTransport : Closeable {
  fun getDaemonLaunchOptions(): DaemonLaunchOptions
  fun createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler

  /**
   * Blocks until the greeting message from the daemon process. Returns null if the stream has reached EOF prematurely.
   */
  @Throws(IOException::class)
  fun readHandshake(): Handshake?

  /**
   * The contract is that invoking close() interrupts any ongoing blocking operations,
   * and makes [readHandshake] throw. The implementations must guarantee that.
   */
  override fun close()
}

internal class EncryptedHandshakeTransport(private val delegate: HandshakeTransport) : HandshakeTransport by delegate {
  private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
    initialize(1024)
  }.genKeyPair()

  override fun getDaemonLaunchOptions(): DaemonLaunchOptions {
    return delegate.getDaemonLaunchOptions().run {
      check(tokenEncryptionOption == null) { "Already encrypted" }
      copy(tokenEncryptionOption = DaemonLaunchOptions.TokenEncryptionOption(keyPair.public))
    }
  }

  override fun readHandshake(): Handshake? {
    return delegate.readHandshake()?.let { handshake ->
      handshake.toBuilder().apply {
        token = keyPair.private.rsaDecrypt(token)
      }.build()
    }
  }
}

internal fun HandshakeTransport.encrypted(): HandshakeTransport = EncryptedHandshakeTransport(this)


internal abstract class AbstractHandshakeTransportBase<R : HandshakeReader> : HandshakeTransport, ProcessAdapter() {
  protected abstract val handshakeReader: R

  override fun getDaemonLaunchOptions() = DaemonLaunchOptions(handshakeOption = getHandshakeOption())
  abstract fun getHandshakeOption(): DaemonLaunchOptions.HandshakeOption

  final override fun createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return createProcessHandler(daemonCommandLine).also {
      it.addProcessListener(this)
    }
  }

  protected open fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return OSProcessHandler.Silent(daemonCommandLine)
  }

  override fun readHandshake(): Handshake? {
    return handshakeReader.read(Handshake::parseDelimitedFrom).also {
      if (it?.port == 0) {
        throw IOException("Invalid/incomplete handshake message from daemon: $it")
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

  override fun close() = handshakeReader.close()
}

internal abstract class AbstractHandshakeTransport<R : HandshakeReader>(override val handshakeReader: R) : AbstractHandshakeTransportBase<R>()

internal class HandshakeSocketTransport(
  port: Int = 0
) : AbstractHandshakeTransport<HandshakeSocketReader>(HandshakeSocketReader(port)) {
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Port(handshakeReader.port)
}

internal class HandshakeStdoutTransport : AbstractHandshakeTransportBase<HandshakeStreamReader>() {
  override lateinit var handshakeReader: HandshakeStreamReader

  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Stdout

  override fun createProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    return object : OSProcessHandler.Silent(daemonCommandLine) {
      override fun createProcessOutReader(): Reader {
        return BaseInputStreamReader(InputStream.nullInputStream())  // don't let the process handler touch the stdout stream
      }
    }.also {
      handshakeReader = HandshakeStreamReader(it.process.inputStream)
    }
  }
}

internal open class HandshakeFileTransport(
  handshakeReader: HandshakeFileReader
) : AbstractHandshakeTransport<HandshakeFileReader>(handshakeReader) {
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.File(handshakeReader.path.toAbsolutePath())
}

internal class HandshakeUnixFifoTransport(
  path: Path = FileUtil.generateRandomTemporaryPath().toPath()
) : HandshakeFileTransport(HandshakeUnixFifoReader(path))

