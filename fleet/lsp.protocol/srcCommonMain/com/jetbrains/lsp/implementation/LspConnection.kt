package com.jetbrains.lsp.implementation

import org.jetbrains.annotations.TestOnly

interface LspConnection {
  val input: ByteReader
  val output: ByteWriter

  @TestOnly
  fun isAlive(): Boolean
  fun close()
}

