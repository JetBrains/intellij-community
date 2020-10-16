// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.execution.process.mediator.daemon

import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal interface DaemonHelloWriter : Closeable {
  fun write(doWrite: (OutputStream) -> Unit)
  override fun close()
}

internal open class DaemonHelloStreamWriter(private val outputStream: OutputStream) : DaemonHelloWriter {
  override fun write(doWrite: (OutputStream) -> Unit) = outputStream.let(doWrite)
  override fun close() = outputStream.close()
}

internal object DaemonHelloStdoutWriter : DaemonHelloStreamWriter(System.out)
internal class DaemonHelloFileWriter(path: Path) : DaemonHelloStreamWriter(Files.newOutputStream(path, StandardOpenOption.WRITE))

internal class DaemonHelloSocketWriter(port: UShort) : DaemonHelloStreamWriter(createSocket(port).getOutputStream()) {
  companion object {
    private fun createSocket(port: UShort) = Socket(InetAddress.getLoopbackAddress(), port.toInt())
  }
}
