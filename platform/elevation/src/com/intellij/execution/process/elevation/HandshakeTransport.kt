// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.process.mediator.util.rsaDecrypt
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator

internal interface HandshakeTransport : Closeable {
  fun getDaemonLaunchOptions(): DaemonLaunchOptions

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


internal abstract class AbstractHandshakeTransportBase<R : HandshakeReader> : HandshakeTransport {
  protected abstract val handshakeReader: R

  override fun getDaemonLaunchOptions() = DaemonLaunchOptions(handshakeOption = getHandshakeOption())
  abstract fun getHandshakeOption(): DaemonLaunchOptions.HandshakeOption

  override fun readHandshake(): Handshake? {
    return handshakeReader.read(Handshake::parseDelimitedFrom).also {
      if (it?.port == 0) {
        throw IOException("Invalid/incomplete handshake message from daemon: $it")
      }
    }
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
  public override lateinit var handshakeReader: HandshakeStreamReader

  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.Stdout
}

internal open class HandshakeFileTransport(
  handshakeReader: HandshakeFileReader
) : AbstractHandshakeTransport<HandshakeFileReader>(handshakeReader) {
  override fun getHandshakeOption() = DaemonLaunchOptions.HandshakeOption.File(handshakeReader.path.toAbsolutePath())
}

internal class HandshakeUnixFifoTransport(
  path: Path
) : HandshakeFileTransport(HandshakeUnixFifoReader(path))

