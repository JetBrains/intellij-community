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
import com.intellij.util.nullize
import java.security.MessageDigest
import java.util.*

internal val LOG = Logger.getInstance(CredentialStore::class.java)

internal interface CredentialStore {
  fun get(key: String): String?

  // passed byte array will be cleared
  fun set(key: String, password: ByteArray?)
}

internal fun getRawKey(key: String, requestor: Class<*>?) = if (requestor == null) key else "${requestor.name}/$key"

internal fun toOldKey(hash: ByteArray) = "old-hashed-key|" + Base64.getEncoder().encodeToString(hash)

internal fun toOldKey(newKey: String) = toOldKey(MessageDigest.getInstance("SHA-256").digest(newKey.toByteArray()))

fun joinData(user: String?, password: String?) = "${StringUtil.escapeChars(user.orEmpty(), '\\', '@')}@$password"

fun splitData(data: String): Credentials? {
  if (data.isEmpty()) {
    return null
  }

  val list = parseString(data, '@')
  val result = Credentials(list.getOrNull(0), list.getOrNull(1))
  return if (result.isFulfilled()) result else null
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
  }
  while (c != null)

  return result
}

class Credentials(user: String?, password: String?) {
  val user = user.nullize()
  val password = password.nullize()

  override fun equals(other: Any?): Boolean {
    if (other !is Credentials) return false
    return user == other.user && password == other.password
  }

  override fun hashCode() = (user?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)

  override fun toString() = joinData(user, password)
}

fun Credentials?.isFulfilled() = this != null && user != null && password != null