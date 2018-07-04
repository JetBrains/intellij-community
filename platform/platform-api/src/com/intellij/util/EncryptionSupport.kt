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

import java.nio.ByteBuffer
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// opposite to PropertiesEncryptionSupport, we store iv length, but not body length
open class EncryptionSupport(private val key: Key = SecretKeySpec(generateAesKey(), "AES")) {
  open fun encrypt(data: ByteArray, size: Int = data.size): ByteArray = encrypt(data, size, key)

  open fun decrypt(data: ByteArray): ByteArray = decrypt(data, key)
}

fun generateAesKey(): ByteArray {
  // http://security.stackexchange.com/questions/14068/why-most-people-use-256-bit-encryption-instead-of-128-bit
  val bytes = ByteArray(16)
  SecureRandom().nextBytes(bytes)
  return bytes
}

private fun encrypt(message: ByteArray, size: Int, key: Key): ByteArray {
  val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
  cipher.init(Cipher.ENCRYPT_MODE, key)
  val body = cipher.doFinal(message, 0, size)
  val iv = cipher.iv

  val byteBuffer = ByteBuffer.wrap(ByteArray(4 + iv.size + body.size))
  byteBuffer.putInt(iv.size)
  byteBuffer.put(iv)
  byteBuffer.put(body)
  return byteBuffer.array()
}

private fun decrypt(data: ByteArray, key: Key): ByteArray {
  val byteBuffer = ByteBuffer.wrap(data)
  @Suppress("UsePropertyAccessSyntax")
  val ivLength = byteBuffer.getInt()
  val ciph = Cipher.getInstance("AES/CBC/PKCS5Padding")
  ciph.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(data, byteBuffer.position(), ivLength))
  val dataOffset = byteBuffer.position() + ivLength
  return ciph.doFinal(data, dataOffset, data.size - dataOffset)
}