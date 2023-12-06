// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.toByteArray
import java.nio.CharBuffer
import java.security.SecureRandom

internal val LOG: Logger
  get() = logger<PasswordSafeSettings>()

internal fun joinData(user: String?, password: OneTimeString?): ByteArray? {
  if (user == null && password == null) {
    return null
  }

  val builder = StringBuilder(user.orEmpty())
  StringUtil.escapeChar(builder, '\\')
  StringUtil.escapeChar(builder, '@')
  if (password != null) {
    builder.append('@')
    password.appendTo(builder)
  }

  val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(builder))
  // clear password
  builder.setLength(0)
  return buffer.toByteArray()
}

internal fun splitData(data: String?): Credentials? {
  if (data.isNullOrEmpty()) {
    return null
  }

  val list = parseString(data, '@')
  return Credentials(list.getOrNull(0), list.getOrNull(1))
}

private const val ESCAPING_CHAR = '\\'

private fun parseString(data: String, @Suppress("SameParameterValue") delimiter: Char): List<String> {
  val part = StringBuilder()
  val result = ArrayList<String>(2)
  var i = 0
  var c: Char?
  do {
    c = data.getOrNull(i++)
    if (c != null && c != delimiter) {
      if (c == ESCAPING_CHAR) {
        c = data.getOrNull(i++)
      }

      if (c != null) {
        part.append(c)
        continue
      }
    }

    result.add(part.toString())
    part.setLength(0)

    if (i < data.length) {
      result.add(data.substring(i))
      break
    }
  }
  while (c != null)

  return result
}

fun createSecureRandom(): SecureRandom {
  // do not use SecureRandom.getInstanceStrong()
  // https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/
  // it leads to blocking without any advantages
  return SecureRandom()
}

@Synchronized
internal fun SecureRandom.generateBytes(size: Int): ByteArray {
  val result = ByteArray(size)
  nextBytes(result)
  return result
}