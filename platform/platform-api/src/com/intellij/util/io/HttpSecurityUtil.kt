// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import java.io.ByteArrayOutputStream
import java.nio.CharBuffer
import java.util.*

object HttpSecurityUtil {
  const val AUTHORIZATION_HEADER_NAME = "Authorization"

  @JvmStatic
  fun createBasicAuthHeaderValue(username: String, password: CharArray): String {
    val stream = ByteArrayOutputStream()
    stream.write(("$username:").toByteArray())

    val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
    stream.write(Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit()))
    Arrays.fill(byteBuffer.array(), 0.toByte())

    val encodedCredentials = Base64.getEncoder().encode(stream.toByteArray())
    return String(encodedCredentials)
  }
}