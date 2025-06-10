package com.jetbrains.lsp.implementation

import org.jetbrains.annotations.TestOnly
import java.io.InputStream
import java.io.OutputStream

interface LspConnection {
  val inputStream: InputStream
  val outputStream: OutputStream

  @TestOnly
  fun disconnect()

  @TestOnly
  fun isAlive(): Boolean
}