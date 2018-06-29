/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.credentialStore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EncryptionSupport
import com.intellij.util.generateAesKey
import com.intellij.util.io.toByteArray
import java.nio.CharBuffer
import java.security.MessageDigest
import java.util.*
import javax.crypto.spec.SecretKeySpec

internal val LOG = Logger.getInstance(CredentialStore::class.java)

private fun toOldKey(hash: ByteArray) = "old-hashed-key|" + Base64.getEncoder().encodeToString(hash)

internal fun toOldKeyAsIdentity(hash: ByteArray) = CredentialAttributes(SERVICE_NAME_PREFIX, toOldKey(hash))

fun toOldKey(requestor: Class<*>, userName: String): CredentialAttributes {
  return CredentialAttributes(SERVICE_NAME_PREFIX, toOldKey(MessageDigest.getInstance("SHA-256").digest("${requestor.name}/$userName".toByteArray())))
}

fun joinData(user: String?, password: OneTimeString?): ByteArray? {
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

fun splitData(data: String?): Credentials? {
  if (data.isNullOrEmpty()) {
    return null
  }

  val list = parseString(data!!, '@')
  return Credentials(list.getOrNull(0), list.getOrNull(1))
}

private const val ESCAPING_CHAR = '\\'

private fun parseString(data: String, delimiter: Char): List<String> {
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

// check isEmpty before
@JvmOverloads
fun Credentials.serialize(storePassword: Boolean = true): ByteArray = joinData(userName, if (storePassword) password else null)!!

fun SecureString(value: CharSequence): SecureString = SecureString(Charsets.UTF_8.encode(CharBuffer.wrap(value)).toByteArray())

class SecureString(value: ByteArray) {
  companion object {
    private val encryptionSupport = EncryptionSupport(SecretKeySpec(generateAesKey(), "AES"))
  }

  private val data = encryptionSupport.encrypt(value)

  fun get(clearable: Boolean = true): OneTimeString = OneTimeString(encryptionSupport.decrypt(data), clearable = clearable)
}