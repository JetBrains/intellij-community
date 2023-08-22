// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.gpg.Pgp
import com.intellij.credentialStore.windows.WindowsCryptUtils
import com.intellij.jna.JnaLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.toByteArray
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val builtInEncryptionKey = SecretKeySpec(byteArrayOf(
  0x50, 0x72, 0x6f.toByte(), 0x78.toByte(), 0x79.toByte(), 0x20.toByte(),
  0x43.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x66.toByte(), 0x69.toByte(), 0x67.toByte(),
  0x20.toByte(), 0x53.toByte(), 0x65.toByte(), 0x63.toByte()), "AES")

// opposite to PropertiesEncryptionSupport, we store iv length, but not body length
internal interface EncryptionSupport {
  fun encrypt(data: ByteArray): ByteArray

  fun decrypt(data: ByteArray): ByteArray
}

enum class EncryptionType {
  BUILT_IN, CRYPT_32, PGP_KEY
}

fun getDefaultEncryptionType() = if (SystemInfo.isWindows) EncryptionType.CRYPT_32 else EncryptionType.BUILT_IN

private open class AesEncryptionSupport(private val key: Key) : EncryptionSupport {
  companion object {
    private fun encrypt(message: ByteArray, key: Key): ByteArray {
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val body = cipher.doFinal(message, 0, message.size)
      val iv = cipher.iv

      val byteBuffer = ByteBuffer.wrap(ByteArray(4 + iv.size + body.size))
      byteBuffer.putInt(iv.size)
      byteBuffer.put(iv)
      byteBuffer.put(body)
      return byteBuffer.array()
    }

    private fun decrypt(data: ByteArray, key: Key): ByteArray {
      val byteBuffer = ByteBuffer.wrap(data)
      val ivLength = byteBuffer.getInt()
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(data, byteBuffer.position(), ivLength))
      val dataOffset = byteBuffer.position() + ivLength
      return cipher.doFinal(data, dataOffset, data.size - dataOffset)
    }
  }

  override fun encrypt(data: ByteArray) = encrypt(data, key)

  override fun decrypt(data: ByteArray) = decrypt(data, key)
}

private class WindowsCrypt32EncryptionSupport(key: Key) : AesEncryptionSupport(key) {
  override fun encrypt(data: ByteArray) = WindowsCryptUtils.protect(super.encrypt(data))

  override fun decrypt(data: ByteArray) = super.decrypt(WindowsCryptUtils.unprotect(data))
}

private class PgpKeyEncryptionSupport(private val encryptionSpec: EncryptionSpec) : EncryptionSupport {
  override fun encrypt(data: ByteArray) = Pgp().encrypt(data, encryptionSpec.pgpKeyId!!)

  override fun decrypt(data: ByteArray) = Pgp().decrypt(data)
}

data class EncryptionSpec(val type: EncryptionType, val pgpKeyId: String?)

internal fun createEncryptionSupport(spec: EncryptionSpec): EncryptionSupport {
  return when (spec.type) {
    EncryptionType.BUILT_IN -> createBuiltInOrCrypt32EncryptionSupport(false)
    EncryptionType.CRYPT_32 -> createBuiltInOrCrypt32EncryptionSupport(true)
    EncryptionType.PGP_KEY -> PgpKeyEncryptionSupport(spec)
  }
}

internal fun createBuiltInOrCrypt32EncryptionSupport(isCrypt32: Boolean): EncryptionSupport {
  if (isCrypt32) {
    if (!SystemInfo.isWindows) {
      throw IllegalArgumentException("Crypt32 encryption is supported only on Windows")
    }
    if (JnaLoader.isLoaded()) {
      return WindowsCrypt32EncryptionSupport(builtInEncryptionKey)
    }
  }

  return AesEncryptionSupport(builtInEncryptionKey)
}

fun CharArray.toByteArrayAndClear(): ByteArray {
  val charBuffer = CharBuffer.wrap(this)
  val byteBuffer = Charsets.UTF_8.encode(charBuffer)
  fill(0.toChar())
  return byteBuffer.toByteArray(isClear = true)
}
