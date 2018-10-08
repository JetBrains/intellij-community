// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import java.nio.ByteBuffer
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// opposite to PropertiesEncryptionSupport, we store iv length, but not body length
open class EncryptionSupport(private val key: Key = SecretKeySpec(generateAesKey(), "AES")) {
  open fun encrypt(data: ByteArray, size: Int = data.size) = encrypt(data, size, key)

  open fun decrypt(data: ByteArray) = decrypt(data, key)
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
  val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
  cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(data, byteBuffer.position(), ivLength))
  val dataOffset = byteBuffer.position() + ivLength
  return cipher.doFinal(data, dataOffset, data.size - dataOffset)
}