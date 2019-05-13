// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.net.NetUtils
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

private const val BLOCK_SIZE = 16 * 1024
private val CHARSET_PATTERN = Pattern.compile("charset=([^;]+)")

internal object HttpUrlConnectionUtil {
  @JvmStatic
  @Throws(IOException::class, ProcessCanceledException::class)
  fun readBytes(inputStream: InputStream, connection: URLConnection, progressIndicator: ProgressIndicator?): BufferExposingByteArrayOutputStream {
    val contentLength = connection.contentLength
    val out = BufferExposingByteArrayOutputStream(if (contentLength > 0) contentLength else BLOCK_SIZE)
    NetUtils.copyStreamContent(progressIndicator, inputStream, out, contentLength)
    return out
  }

  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class, ProcessCanceledException::class)
  fun readString(inputStream: InputStream, connection: URLConnection, progressIndicator: ProgressIndicator? = null): String {
    val byteStream = readBytes(inputStream, connection, progressIndicator)
    return if (byteStream.size() == 0) "" else String(byteStream.internalBuffer, 0, byteStream.size(), connection.getCharset())
  }

  @Throws(IOException::class)
  @JvmStatic
  fun URLConnection.getCharset(): Charset {
    val contentType = contentType
    if (!contentType.isNullOrEmpty()) {
      val m = CHARSET_PATTERN.matcher(contentType)
      if (m.find()) {
        try {
          return Charset.forName(StringUtil.unquoteString(m.group(1)))
        }
        catch (e: IllegalArgumentException) {
          throw IOException("unknown charset ($contentType)", e)
        }
      }
    }
    return StandardCharsets.UTF_8
  }
}