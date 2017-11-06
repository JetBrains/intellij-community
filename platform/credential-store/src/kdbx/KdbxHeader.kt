/*
 * Copyright 2015 Jo Rabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.credentialStore.kdbx

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.AESFastEngine
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.io.CipherOutputStream
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * This class represents the header portion of a KeePass KDBX file or stream. The header is received in
 * plain text and describes the encryption and compression of the remainder of the file.
 * It is a factory for encryption and decryption streams and contains a hash of its own serialization.
 * While KDBX streams are Little-Endian, data is passed to and from this class in standard Java byte order.
 * @author jo
 */
/**
 * This UUID denotes that AES Cipher is in use. No other values are known.
 */
private val AES_CIPHER = UUID.fromString("31C1F2E6-BF71-4350-BE58-05216AFC5AFF")

internal class KdbxHeader {
  /**
   * The ordinal 0 represents uncompressed and 1 GZip compressed
   */
  enum class CompressionFlags {
    NONE, GZIP
  }

  /**
   * The ordinals represent various types of encryption that may be applied to fields within the unencrypted data
   */
  enum class ProtectedStreamAlgorithm {
    NONE, ARC_FOUR, SALSA_20
  }

  /* the cipher in use */
  var cipherUuid = AES_CIPHER!!
    private set

  /* whether the data is compressed */
  var compressionFlags = CompressionFlags.GZIP
    private set

  var masterSeed: ByteArray
  var transformSeed: ByteArray
  var transformRounds: Long = 6000
  var encryptionIv: ByteArray
  var protectedStreamKey: ByteArray
  var protectedStreamAlgorithm = ProtectedStreamAlgorithm.SALSA_20
    private set

  /* these bytes appear in cipher text immediately following the header */
  var streamStartBytes = ByteArray(32)
  /* not transmitted as part of the header, used in the XML payload, so calculated
   * on transmission or receipt */
  var headerHash: ByteArray? = null

  init {
    val random = SecureRandom()
    masterSeed = random.generateSeed(32)
    transformSeed = random.generateSeed(32)
    encryptionIv = random.generateSeed(16)
    protectedStreamKey = random.generateSeed(32)
  }

  /**
   * Create a decrypted input stream using supplied digest and this header
   * apply decryption to the passed encrypted input stream
   */
  fun createDecryptedStream(digest: ByteArray, inputStream: InputStream): InputStream {
    val finalKeyDigest = getFinalKeyDigest(digest, masterSeed, transformSeed, transformRounds)
    return getDecryptedInputStream(inputStream, finalKeyDigest, encryptionIv)
  }

  /**
   * Create an unencrypted output stream using the supplied digest and this header
   * and use the supplied output stream to write encrypted data.
   */
  fun createEncryptedStream(digest: ByteArray, outputStream: OutputStream): OutputStream {
    val finalKeyDigest = getFinalKeyDigest(digest, masterSeed, transformSeed, transformRounds)
    return getEncryptedOutputStream(outputStream, finalKeyDigest, encryptionIv)
  }

  fun setCipherUuid(uuid: ByteArray) {
    val b = ByteBuffer.wrap(uuid)
    val incoming = UUID(b.long, b.getLong(8))
    if (incoming != AES_CIPHER) {
      throw IllegalStateException("Unknown Cipher UUID $incoming")
    }
    this.cipherUuid = incoming
  }

  fun setCompressionFlags(flags: Int) {
    this.compressionFlags = CompressionFlags.values()[flags]
  }

  fun setInnerRandomStreamId(innerRandomStreamId: Int) {
    this.protectedStreamAlgorithm = ProtectedStreamAlgorithm.values()[innerRandomStreamId]
  }
}

private fun getFinalKeyDigest(key: ByteArray, masterSeed: ByteArray, transformSeed: ByteArray, transformRounds: Long): ByteArray {
  val engine = AESEngine()
  engine.init(true, KeyParameter(transformSeed))

  // copy input key
  val transformedKey = ByteArray(key.size)
  System.arraycopy(key, 0, transformedKey, 0, transformedKey.size)

  // transform rounds times
  for (rounds in 0 until transformRounds) {
    engine.processBlock(transformedKey, 0, transformedKey, 0)
    engine.processBlock(transformedKey, 16, transformedKey, 16)
  }

  val md = sha256MessageDigest()
  val transformedKeyDigest = md.digest(transformedKey)
  md.update(masterSeed)
  return md.digest(transformedKeyDigest)
}

fun sha256MessageDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

/**
 * Create a decrypted input stream from an encrypted one
 */
private fun getDecryptedInputStream(encryptedInputStream: InputStream, keyData: ByteArray, ivData: ByteArray): InputStream {
  val keyAndIV = ParametersWithIV(KeyParameter(keyData), ivData)
  val pbbc = PaddedBufferedBlockCipher(CBCBlockCipher(AESFastEngine()))
  pbbc.init(false, keyAndIV)
  return CipherInputStream(encryptedInputStream, pbbc)
}

/**
 * Create an encrypted output stream from an unencrypted output stream
 */
private fun getEncryptedOutputStream(decryptedOutputStream: OutputStream, keyData: ByteArray, ivData: ByteArray): OutputStream {
  val keyAndIV = ParametersWithIV(KeyParameter(keyData), ivData)
  val pbbc = PaddedBufferedBlockCipher(CBCBlockCipher(AESFastEngine()))
  pbbc.init(true, keyAndIV)
  return CipherOutputStream(decryptedOutputStream, pbbc)
}