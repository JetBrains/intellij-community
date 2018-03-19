// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.net.NetUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLConnection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

private const val BLOCK_SIZE = 16 * 1024
private val CHARSET_PATTERN = Pattern.compile("charset=([^;]+)")

internal object HttpUrlConnectionUtil {
  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class, ProcessCanceledException::class)
  fun URLConnection.readBytes(progressIndicator: ProgressIndicator?, isReadFromErrorStream: Boolean = false): BufferExposingByteArrayOutputStream {
    val contentLength = contentLength
    val out = BufferExposingByteArrayOutputStream(if (contentLength > 0) contentLength else BLOCK_SIZE)
    NetUtils.copyStreamContent(progressIndicator, if (isReadFromErrorStream && this is HttpURLConnection) errorStream else inputStream, out, contentLength)
    return out
  }

  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class, ProcessCanceledException::class)
  fun URLConnection.readString(progressIndicator: ProgressIndicator? = null, isReadFromErrorStream: Boolean = false): String {
    val byteStream = readBytes(progressIndicator, isReadFromErrorStream)
    return if (byteStream.size() == 0) "" else String(byteStream.internalBuffer, 0, byteStream.size(), getCharset())
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