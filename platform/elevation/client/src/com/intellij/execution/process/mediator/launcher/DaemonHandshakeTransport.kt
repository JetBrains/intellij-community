// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.launcher

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.process.mediator.util.rsaDecrypt
import com.intellij.util.io.*
import com.intellij.util.io.processHandshake.ProcessHandshakeTransport
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator


interface DaemonHandshakeTransport : ProcessHandshakeTransport<Handshake> {
  fun getDaemonLaunchOptions(): DaemonLaunchOptions

  companion object
}

fun DaemonHandshakeTransport.Companion.createProcessStdoutTransport(launchOptions: DaemonLaunchOptions): DaemonHandshakeTransport =
  StdoutTransport(launchOptions)

fun DaemonHandshakeTransport.Companion.createUnixFifoTransport(launchOptions: DaemonLaunchOptions, path: Path): DaemonHandshakeTransport =
  UnixFifoTransport(launchOptions, path)

fun DaemonHandshakeTransport.Companion.createSocketTransport(launchOptions: DaemonLaunchOptions, port: Int = 0): DaemonHandshakeTransport =
  SocketTransport(launchOptions, port)

fun DaemonHandshakeTransport.encrypted(): DaemonHandshakeTransport = EncryptedHandshakeTransport(this)

private class EncryptedHandshakeTransport(private val delegate: DaemonHandshakeTransport) : DaemonHandshakeTransport by delegate {
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


private abstract class AbstractHandshakeTransport(private val launchOptions: DaemonLaunchOptions) : DaemonHandshakeTransport {
  protected abstract val inputHandle: InputHandle

  override fun getDaemonLaunchOptions() = launchOptions.copy(handshakeOption = getHandshakeOption())
  abstract fun getHandshakeOption(): DaemonLaunchOptions.HandshakeOption

  override fun readHandshake(): Handshake? {
    return Handshake.parseDelimitedFrom(inputHandle.inputStream)
  }

  override fun close() = inputHandle.close()
}

private class SocketTransport(
  launchOptions: DaemonLaunchOptions,
  port: Int = 0
) : AbstractHandshakeTransport(launchOptions) {
  override val inputHandle = SocketInputHandle(port)
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Port(inputHandle.localPort)
}

private class StdoutTransport(launchOptions: DaemonLaunchOptions) : AbstractHandshakeTransport(launchOptions),
                                                                    DaemonHandshakeTransport {
  override lateinit var inputHandle: StreamInputHandle
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Stdout

  override fun createProcessHandler(commandLine: GeneralCommandLine): BaseOSProcessHandler {
    return object : OSProcessHandler.Silent(commandLine) {
      override fun createProcessOutReader(): Reader {
        return BaseInputStreamReader(InputStream.nullInputStream())  // don't let the process handler touch the stdout stream
      }
    }.also {
      inputHandle = StreamInputHandle(it.process.inputStream)
    }
  }
}

private class UnixFifoTransport(
  launchOptions: DaemonLaunchOptions,
  path: Path,
) : AbstractHandshakeTransport(launchOptions) {
  override val inputHandle = UnixFifoInputHandle(path)
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.File(inputHandle.path.toAbsolutePath())
}
