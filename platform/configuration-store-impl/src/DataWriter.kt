// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.util.LineSeparator
import com.intellij.util.io.outputStream
import com.intellij.util.io.safeOutputStream
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.OutputStream
import java.io.Writer
import java.nio.file.Path

@Internal
interface DataWriter {
  fun writeTo(output: OutputStream, lineSeparator: LineSeparator, filter: DataWriterFilter? = null)

  fun hasData(filter: DataWriterFilter): Boolean

  fun writeTo(file: Path, requestor: Any?, lineSeparator: LineSeparator, useXmlProlog: Boolean) {
    val safe = SafeWriteRequestor.shouldUseSafeWrite(requestor)
    (if (safe) file.safeOutputStream() else file.outputStream()).use { out ->
      if (useXmlProlog) {
        out.write(XML_PROLOG)
        out.write(lineSeparator.separatorBytes)
      }
      writeTo(out, lineSeparator)
    }
  }

  fun toBufferExposingByteArray(lineSeparator: LineSeparator): BufferExposingByteArrayOutputStream {
    val out = BufferExposingByteArrayOutputStream(1024)
    out.use { writeTo(it, lineSeparator) }
    return out
  }
}

@Internal
interface DataWriterFilter {
  enum class ElementLevel { ZERO, FIRST }

  fun toElementFilter(): JDOMUtil.ElementOutputFilter

  fun hasData(element: Element): Boolean
}

internal abstract class StringDataWriter : DataWriter {
  final override fun writeTo(output: OutputStream, lineSeparator: LineSeparator, filter: DataWriterFilter?) {
    output.bufferedWriter().use { writer ->
      writeTo(writer = writer, lineSeparator = lineSeparator.separatorString, filter = filter)
    }
  }

  internal abstract fun writeTo(writer: Writer, lineSeparator: String, filter: DataWriterFilter?)
}
