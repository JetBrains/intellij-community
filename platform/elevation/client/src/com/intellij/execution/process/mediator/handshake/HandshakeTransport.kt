// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.handshake

import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.process.mediator.util.rsaDecrypt
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator

interface HandshakeTransport<H> : Closeable {
  /**
   * Blocks until the greeting message from the daemon process.
   * Returns null if the stream has reached EOF prematurely.
   */
  @Throws(IOException::class)
  fun readHandshake(): H?
}

interface ProcessStdoutHandshakeTransport<H> : HandshakeTransport<H> {
  fun initStream(inputStream: InputStream)
}


interface DaemonHandshakeTransport : HandshakeTransport<Handshake> {
  fun getDaemonLaunchOptions(): DaemonLaunchOptions

  @Throws(IOException::class)
  override fun readHandshake(): Handshake?

  override fun close()

  companion object
}

interface ProcessStdoutDaemonHandshakeTransport : DaemonHandshakeTransport, ProcessStdoutHandshakeTransport<Handshake> {
  override fun initStream(inputStream: InputStream)
}

fun DaemonHandshakeTransport.Companion.createProcessStdoutTransport(launchOptions: DaemonLaunchOptions): ProcessStdoutDaemonHandshakeTransport =
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
  protected abstract val handshakeReader: HandshakeReader

  override fun getDaemonLaunchOptions() = launchOptions.copy(handshakeOption = getHandshakeOption())
  abstract fun getHandshakeOption(): DaemonLaunchOptions.HandshakeOption

  override fun readHandshake(): Handshake? {
    return Handshake.parseDelimitedFrom(handshakeReader.inputStream)
  }

  override fun close() = handshakeReader.close()
}

private class SocketTransport(
  launchOptions: DaemonLaunchOptions,
  port: Int = 0
) : AbstractHandshakeTransport(launchOptions) {
  override val handshakeReader = HandshakeSocketReader(port)
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Port(handshakeReader.localPort)
}

private class StdoutTransport(launchOptions: DaemonLaunchOptions) : AbstractHandshakeTransport(launchOptions),
                                                                    ProcessStdoutDaemonHandshakeTransport {
  override lateinit var handshakeReader: HandshakeStreamReader
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Stdout

  override fun initStream(inputStream: InputStream) {
    handshakeReader = HandshakeStreamReader(inputStream)
  }
}

private open class FileTransport(
  launchOptions: DaemonLaunchOptions,
  override val handshakeReader: HandshakeFileReader
) : AbstractHandshakeTransport(launchOptions) {
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.File(handshakeReader.path.toAbsolutePath())
}

private class UnixFifoTransport(
  launchOptions: DaemonLaunchOptions,
  path: Path
) : FileTransport(launchOptions, HandshakeUnixFifoReader(path))
