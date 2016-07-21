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
package com.intellij.util

import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class EncryptionSupport(private val key: Key = SecretKeySpec(generateAesKey(), "AES")) {
  open fun encrypt(data: ByteArray) = encrypt(data, key)

  open fun decrypt(data: ByteArray) = decrypt(data, key)
}

fun generateAesKey(): ByteArray {
  // http://security.stackexchange.com/questions/14068/why-most-people-use-256-bit-encryption-instead-of-128-bit
  val bytes = ByteArray(16)
  SecureRandom().nextBytes(bytes)
  return bytes
}

private fun encrypt(msgBytes: ByteArray, key: Key): ByteArray {
  val ciph = Cipher.getInstance("AES/CBC/PKCS5Padding")
  ciph.init(Cipher.ENCRYPT_MODE, key)
  val body = ciph.doFinal(msgBytes)
  val iv = ciph.iv

  val data = ByteArray(4 + iv.size + body.size)

  val length = body.size
  data[0] = (length shr 24 and 0xFF).toByte()
  data[1] = (length shr 16 and 0xFF).toByte()
  data[2] = (length shr 8 and 0xFF).toByte()
  data[3] = (length and 0xFF).toByte()

  System.arraycopy(iv, 0, data, 4, iv.size)
  System.arraycopy(body, 0, data, 4 + iv.size, body.size)
  return data
}

private fun decrypt(data: ByteArray, key: Key): ByteArray {
  var bodyLength = data[0].toInt() and 0xFF.toInt()
  bodyLength = (bodyLength shl 8) + data[1] and 0xFF
  bodyLength = (bodyLength shl 8) + data[2] and 0xFF
  bodyLength = (bodyLength shl 8) + data[3] and 0xFF

  val ivlength = data.size - 4 - bodyLength

  val ciph = Cipher.getInstance("AES/CBC/PKCS5Padding")
  ciph.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(data, 4, ivlength))
  return ciph.doFinal(data, 4 + ivlength, bodyLength)
}