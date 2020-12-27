// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import java.io.Closeable
import java.io.IOException
import java.io.InputStream

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
