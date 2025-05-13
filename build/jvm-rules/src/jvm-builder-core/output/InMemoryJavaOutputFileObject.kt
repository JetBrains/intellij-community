// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker.core.output

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.net.URI
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.JavaFileObject

class InMemoryJavaOutputFileObject(
  @JvmField val path: String,
  @JvmField val source: File,
) : JavaFileObject {
  @JvmField var content: ByteArray? = null

  override fun getKind(): JavaFileObject.Kind = JavaFileObject.Kind.CLASS

  override fun isNameCompatible(simpleName: String, kind: JavaFileObject.Kind): Boolean {
    return kind == JavaFileObject.Kind.CLASS && simpleName == path
  }

  override fun getNestingKind(): NestingKind? = null

  override fun getAccessLevel(): Modifier? = null

  override fun toUri(): URI = throw UnsupportedOperationException()

  override fun getName(): String = path

  override fun openOutputStream(): OutputStream {
    val buffer = ByteBufAllocator.DEFAULT.buffer()
    return object : ByteBufOutputStream(buffer, false) {
      private var isClosed = false

      override fun close() {
        if (isClosed) {
          return
        }

        isClosed = true
        try {
          super.close()
          content = ByteBufUtil.getBytes(buffer)
        }
        finally {
          buffer.release()
        }
      }
    }
  }

  override fun openWriter(): Writer = openOutputStream().writer()

  override fun openInputStream(): InputStream = throw IllegalStateException()

  override fun openReader(ignoreEncodingErrors: Boolean): Reader = throw IllegalStateException()

  override fun getCharContent(ignoreEncodingErrors: Boolean): String = throw IllegalStateException()

  override fun getLastModified(): Long = 1

  // never called, JPS impl also implements as `return false`
  override fun delete() = throw IllegalStateException()
}