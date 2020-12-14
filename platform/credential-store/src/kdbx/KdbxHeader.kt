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

import com.google.common.io.LittleEndianDataInputStream
import com.google.common.io.LittleEndianDataOutputStream
import com.intellij.credentialStore.generateBytes
import com.intellij.util.ArrayUtilRt
import com.intellij.util.io.DigestUtil
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.io.CipherOutputStream
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.SecureRandom
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * This class represents the header portion of a KeePass KDBX file or stream. The header is received in
 * plain text and describes the encryption and compression of the remainder of the file.
 * It is a factory for encryption and decryption streams and contains a hash of its own serialization.
 * While KDBX streams are Little-Endian, data is passed to and from this class in standard Java byte order.
 * @author jo

 * This UUID denotes that AES Cipher is in use. No other values are known.
 */
private val AES_CIPHER = UUID.fromString("31C1F2E6-BF71-4350-BE58-05216AFC5AFF")

private const val FILE_VERSION_CRITICAL_MASK = 0xFFFF0000.toInt()

private const val SIG1 = 0x9AA2D903.toInt()
private const val SIG2 = 0xB54BFB67.toInt()

private const val FILE_VERSION_32 = 0x00030001

internal fun createProtectedStreamKey(random: SecureRandom) = random.generateBytes(32)

private object HeaderType {
  const val END: Byte = 0
  const val COMMENT: Byte = 1
  const val CIPHER_ID: Byte = 2
  const val COMPRESSION_FLAGS: Byte = 3
  const val MASTER_SEED: Byte = 4
  const val TRANSFORM_SEED: Byte = 5
  const val TRANSFORM_ROUNDS: Byte = 6
  const val ENCRYPTION_IV: Byte = 7
  const val PROTECTED_STREAM_KEY: Byte = 8
  const val STREAM_START_BYTES: Byte = 9
  const val INNER_RANDOM_STREAM_ID: Byte = 10
}

private fun readSignature(input: LittleEndianDataInputStream): Boolean {
  return input.readInt() == SIG1 && input.readInt() == SIG2
}

private fun verifyFileVersion(input: LittleEndianDataInputStream): Boolean {
  return input.readInt() and FILE_VERSION_CRITICAL_MASK <= FILE_VERSION_32 and FILE_VERSION_CRITICAL_MASK
}

internal class KdbxHeader() {
  constructor(inputStream: InputStream) : this() {
    readKdbxHeader(inputStream)
  }

  constructor(random: SecureRandom) : this() {
    masterSeed = random.generateBytes(32)
    transformSeed = random.generateBytes(32)
    encryptionIv = random.generateBytes(16)
    protectedStreamKey = createProtectedStreamKey(random)
  }
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

  // the cipher in use
  private var cipherUuid = AES_CIPHER

  /* whether the data is compressed */
  var compressionFlags = CompressionFlags.GZIP
    private set

  private var masterSeed: ByteArray = ArrayUtilRt.EMPTY_BYTE_ARRAY
  private var transformSeed: ByteArray = ArrayUtilRt.EMPTY_BYTE_ARRAY
  private var transformRounds: Long = 6000
  private var encryptionIv: ByteArray = ArrayUtilRt.EMPTY_BYTE_ARRAY
  var protectedStreamKey: ByteArray = ArrayUtilRt.EMPTY_BYTE_ARRAY
    private set
  private var protectedStreamAlgorithm = ProtectedStreamAlgorithm.SALSA_20

  /* these bytes appear in cipher text immediately following the header */
  var streamStartBytes = ByteArray(32)
    private set
  /* not transmitted as part of the header, used in the XML payload, so calculated
   * on transmission or receipt */
  var headerHash: ByteArray? = null

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
    var out = getEncryptedOutputStream(outputStream, finalKeyDigest, encryptionIv)
    out.write(streamStartBytes)
    out = HashedBlockOutputStream(out)
    return when (compressionFlags) {
      CompressionFlags.GZIP -> GZIPOutputStream(out, HashedBlockOutputStream.BLOCK_SIZE)
      else -> out
    }
  }

  private fun setCipherUuid(uuid: ByteArray) {
    val b = ByteBuffer.wrap(uuid)
    val incoming = UUID(b.long, b.getLong(8))
    if (incoming != AES_CIPHER) {
      throw IllegalStateException("Unknown Cipher UUID $incoming")
    }
    cipherUuid = incoming
  }

  /**
   * Populate a KdbxHeader from the input stream supplied
   */
  private fun readKdbxHeader(inputStream: InputStream) {
    val digest = DigestUtil.sha256()
    // we do not close this stream, otherwise we lose our place in the underlying stream
    val digestInputStream = DigestInputStream(inputStream, digest)
    // we do not close this stream, otherwise we lose our place in the underlying stream
    val input = LittleEndianDataInputStream(digestInputStream)

    if (!readSignature(input)) {
      throw KdbxException("Bad signature")
    }

    if (!verifyFileVersion(input)) {
      throw IllegalStateException("File version did not match")
    }

    while (true) {
      val headerType = input.readByte()
      if (headerType == HeaderType.END) {
        break
      }

      when (headerType) {
        HeaderType.COMMENT -> readHeaderData(input)
        HeaderType.CIPHER_ID -> setCipherUuid(readHeaderData(input))
        HeaderType.COMPRESSION_FLAGS -> {
          compressionFlags = CompressionFlags.values()[readIntHeaderData(input)]
        }
        HeaderType.MASTER_SEED -> masterSeed = readHeaderData(input)
        HeaderType.TRANSFORM_SEED -> transformSeed = readHeaderData(input)
        HeaderType.TRANSFORM_ROUNDS -> transformRounds = readLongHeaderData(input)
        HeaderType.ENCRYPTION_IV -> encryptionIv = readHeaderData(input)
        HeaderType.PROTECTED_STREAM_KEY -> protectedStreamKey = readHeaderData(input)
        HeaderType.STREAM_START_BYTES -> streamStartBytes = readHeaderData(input)
        HeaderType.INNER_RANDOM_STREAM_ID -> {
          protectedStreamAlgorithm = ProtectedStreamAlgorithm.values()[readIntHeaderData(input)]
        }

        else -> throw IllegalStateException("Unknown File Header")
      }
    }

    // consume length etc. following END flag
    readHeaderData(input)

    headerHash = digest.digest()
  }

  /**
   * Write a KdbxHeader to the output stream supplied. The header is updated with the
   * message digest of the written stream.
   */
  fun writeKdbxHeader(outputStream: OutputStream) {
    val messageDigest = DigestUtil.sha256()
    val digestOutputStream = DigestOutputStream(outputStream, messageDigest)
    val output = LittleEndianDataOutputStream(digestOutputStream)

    // write the magic number
    output.writeInt(SIG1)
    output.writeInt(SIG2)
    // write a file version
    output.writeInt(FILE_VERSION_32)

    output.writeByte(HeaderType.CIPHER_ID.toInt())
    output.writeShort(16)
    val b = ByteArray(16)
    val bb = ByteBuffer.wrap(b)
    bb.putLong(cipherUuid.mostSignificantBits)
    bb.putLong(8, cipherUuid.leastSignificantBits)
    output.write(b)

    output.writeByte(HeaderType.COMPRESSION_FLAGS.toInt())
    output.writeShort(4)
    output.writeInt(compressionFlags.ordinal)

    output.writeByte(HeaderType.MASTER_SEED.toInt())
    output.writeShort(masterSeed.size)
    output.write(masterSeed)

    output.writeByte(HeaderType.TRANSFORM_SEED.toInt())
    output.writeShort(transformSeed.size)
    output.write(transformSeed)

    output.writeByte(HeaderType.TRANSFORM_ROUNDS.toInt())
    output.writeShort(8)
    output.writeLong(transformRounds)

    output.writeByte(HeaderType.ENCRYPTION_IV.toInt())
    output.writeShort(encryptionIv.size)
    output.write(encryptionIv)

    output.writeByte(HeaderType.PROTECTED_STREAM_KEY.toInt())
    output.writeShort(protectedStreamKey.size)
    output.write(protectedStreamKey)

    output.writeByte(HeaderType.STREAM_START_BYTES.toInt())
    output.writeShort(streamStartBytes.size)
    output.write(streamStartBytes)

    output.writeByte(HeaderType.INNER_RANDOM_STREAM_ID.toInt())
    output.writeShort(4)
    output.writeInt(protectedStreamAlgorithm.ordinal)

    output.writeByte(HeaderType.END.toInt())
    output.writeShort(0)

    headerHash = digestOutputStream.messageDigest.digest()
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

  val md = DigestUtil.sha256()
  val transformedKeyDigest = md.digest(transformedKey)
  md.update(masterSeed)
  return md.digest(transformedKeyDigest)
}

/**
 * Create a decrypted input stream from an encrypted one
 */
private fun getDecryptedInputStream(encryptedInputStream: InputStream, keyData: ByteArray, ivData: ByteArray): InputStream {
  val keyAndIV = ParametersWithIV(KeyParameter(keyData), ivData)
  val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(AESEngine()))
  cipher.init(false, keyAndIV)
  return CipherInputStream(encryptedInputStream, cipher)
}

/**
 * Create an encrypted output stream from an unencrypted output stream
 */
private fun getEncryptedOutputStream(decryptedOutputStream: OutputStream, keyData: ByteArray, ivData: ByteArray): OutputStream {
  val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(AESEngine()))
  cipher.init(true, ParametersWithIV(KeyParameter(keyData), ivData))
  return CipherOutputStream(decryptedOutputStream, cipher)
}

private fun readIntHeaderData(input: LittleEndianDataInputStream): Int {
  val fieldLength = input.readShort()
  if (fieldLength.toInt() != 4) {
    throw IllegalStateException("Int required but length was $fieldLength")
  }
  return input.readInt()
}

private fun readLongHeaderData(input: LittleEndianDataInputStream): Long {
  val fieldLength = input.readShort()
  if (fieldLength.toInt() != 8) {
    throw IllegalStateException("Long required but length was $fieldLength")
  }
  return input.readLong()
}

private fun readHeaderData(input: LittleEndianDataInputStream): ByteArray {
  val value = ByteArray(input.readShort().toInt())
  input.readFully(value)
  return value
}