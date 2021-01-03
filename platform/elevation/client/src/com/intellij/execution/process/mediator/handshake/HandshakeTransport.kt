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

interface HandshakeTransport : Closeable {
  fun getDaemonLaunchOptions(): DaemonLaunchOptions

  /**
   * Blocks until the greeting message from the daemon process. Returns null if the stream has reached EOF prematurely.
   */
  @Throws(IOException::class)
  fun readHandshake(): Handshake?

  override fun close()

  companion object
}

interface ProcessStdoutHandshakeTransport : HandshakeTransport {
  fun initStream(inputStream: InputStream)
}

fun HandshakeTransport.Companion.createProcessStdoutTransport(): ProcessStdoutHandshakeTransport = StdoutTransport()
fun HandshakeTransport.Companion.createUnixFifoTransport(path: Path): HandshakeTransport = UnixFifoTransport(path)
fun HandshakeTransport.Companion.createSocketTransport(port: Int = 0): HandshakeTransport = SocketTransport(port)

fun HandshakeTransport.encrypted(): HandshakeTransport = EncryptedHandshakeTransport(this)

private class EncryptedHandshakeTransport(private val delegate: HandshakeTransport) : HandshakeTransport by delegate {
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


private abstract class AbstractHandshakeTransport : HandshakeTransport {
  protected abstract val handshakeReader: HandshakeReader

  override fun getDaemonLaunchOptions() = DaemonLaunchOptions(handshakeOption = getHandshakeOption())
  abstract fun getHandshakeOption(): DaemonLaunchOptions.HandshakeOption

  override fun readHandshake(): Handshake? {
    return handshakeReader.read(Handshake::parseDelimitedFrom)
  }

  override fun close() = handshakeReader.close()
}

private class SocketTransport(
  port: Int = 0
) : AbstractHandshakeTransport() {
  override val handshakeReader = HandshakeSocketReader(port)
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Port(handshakeReader.port)
}

private class StdoutTransport : AbstractHandshakeTransport(), ProcessStdoutHandshakeTransport {
  override lateinit var handshakeReader: HandshakeStreamReader
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Stdout

  override fun initStream(inputStream: InputStream) {
    handshakeReader = HandshakeStreamReader(inputStream)
  }
}

private open class FileTransport(
  override val handshakeReader: HandshakeFileReader
) : AbstractHandshakeTransport() {
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.File(handshakeReader.path.toAbsolutePath())
}

private class UnixFifoTransport(
  path: Path
) : FileTransport(HandshakeUnixFifoReader(path))

