package com.jetbrains.lsp.implementation

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.Closeable
import org.jetbrains.annotations.TestOnly

interface LspConnection : Closeable {
  val input: ByteReadChannel
  val output: ByteWriteChannel

  @TestOnly
  fun isAlive(): Boolean
}