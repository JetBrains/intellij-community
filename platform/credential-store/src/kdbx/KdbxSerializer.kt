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
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A KDBX file is little-endian and consists of the following:
 *
 *  1. An unencrypted portion
 *
 *  1. 8 bytes Magic number
 *  1. 4 bytes version
 *  1. A header containing details of the encryption of the remainder of the file
 *
 * The header fields are encoded using a TLV style. The Type is an enumeration encoded in 1 byte.
 * The length is encoded in 2 bytes and the value according to the length denoted. The sequence is
 * terminated by a zero type with 0 length.
 *
 *  1. An encrypted portion
 *
 *  1. A sequence of bytes contained in the header. If they don't match, decryption has not worked.
 *  1. A payload serialized in Hashed Block format.
 *
 * The methods in this class provide support for serializing and deserializing plain text payload content
 * to and from the above format.
 * @author jo
 */
internal object KdbxSerializer {
  /**
   * Provides the payload of a KDBX file as an unencrypted [InputStream].

   * @param credentials credentials for decryption of the stream
   * @param kdbxHeader  a header instance to be populated with values from the stream
   * @param inputStream a KDBX formatted input stream
   * @return an unencrypted input stream, to be read and closed by the caller
   */
  fun createUnencryptedInputStream(credentials: KeePassCredentials, kdbxHeader: KdbxHeader, inputStream: InputStream): InputStream {
    readKdbxHeader(kdbxHeader, inputStream)

    val decryptedInputStream = kdbxHeader.createDecryptedStream(credentials.key, inputStream)
    checkStartBytes(kdbxHeader, decryptedInputStream)
    val blockInputStream = HashedBlockInputStream(decryptedInputStream)
    if (kdbxHeader.compressionFlags == KdbxHeader.CompressionFlags.NONE) {
      return blockInputStream
    }
    return GZIPInputStream(blockInputStream)
  }

  /**
   * Provides an [OutputStream] to be encoded and encrypted in KDBX format

   * @param credentials  credentials for encryption of the stream
   * @param kdbxHeader   a KDBX header to control the formatting and encryption operation
   * @param outputStream output stream to contain the KDBX formatted output
   * @return an unencrypted output stream, to be written to, flushed and closed by the caller
   */
  fun createEncryptedOutputStream(credentials: KeePassCredentials, kdbxHeader: KdbxHeader, outputStream: OutputStream): OutputStream {
    writeKdbxHeader(kdbxHeader, outputStream)

    val encryptedOutputStream = kdbxHeader.createEncryptedStream(credentials.key, outputStream)
    LittleEndianDataOutputStream(encryptedOutputStream).write(kdbxHeader.streamStartBytes)

    val blockOutputStream = HashedBlockOutputStream(encryptedOutputStream)
    if (kdbxHeader.compressionFlags == KdbxHeader.CompressionFlags.NONE) {
      return blockOutputStream
    }
    return GZIPOutputStream(blockOutputStream)
  }
}

private fun checkStartBytes(kdbxHeader: KdbxHeader, decryptedInputStream: InputStream) {
  val startBytes = ByteArray(32)
  LittleEndianDataInputStream(decryptedInputStream).readFully(startBytes)
  if (!Arrays.equals(startBytes, kdbxHeader.streamStartBytes)) {
    throw IllegalStateException("Inconsistent stream bytes")
  }
}

private val SIG1 = 0x9AA2D903.toInt()
private val SIG2 = 0xB54BFB67.toInt()
private val FILE_VERSION_CRITICAL_MASK = 0xFFFF0000.toInt()
private val FILE_VERSION_32 = 0x00030001

private object HeaderType {
  internal val END: Byte = 0
  internal val COMMENT: Byte = 1
  internal val CIPHER_ID: Byte = 2
  internal val COMPRESSION_FLAGS: Byte = 3
  internal val MASTER_SEED: Byte = 4
  internal val TRANSFORM_SEED: Byte = 5
  internal val TRANSFORM_ROUNDS: Byte = 6
  internal val ENCRYPTION_IV: Byte = 7
  internal val PROTECTED_STREAM_KEY: Byte = 8
  internal val STREAM_START_BYTES: Byte = 9
  internal val INNER_RANDOM_STREAM_ID: Byte = 10
}

private fun verifyMagicNumber(input: LittleEndianDataInputStream): Boolean {
  val sig1 = input.readInt()
  val sig2 = input.readInt()
  return sig1 == SIG1 && sig2 == SIG2
}

private fun verifyFileVersion(input: LittleEndianDataInputStream): Boolean {
  return input.readInt() and FILE_VERSION_CRITICAL_MASK <= FILE_VERSION_32 and FILE_VERSION_CRITICAL_MASK
}

/**
 * Populate a KdbxHeader from the input stream supplied

 * @param kdbxHeader  a header to be populated
 * @param inputStream an input stream
 * @return the populated KdbxHeader
 */
internal fun readKdbxHeader(kdbxHeader: KdbxHeader, inputStream: InputStream): KdbxHeader {
  val digest = sha256MessageDigest()
  // we do not close this stream, otherwise we lose our place in the underlying stream
  val digestInputStream = DigestInputStream(inputStream, digest)
  // we do not close this stream, otherwise we lose our place in the underlying stream
  val input = LittleEndianDataInputStream(digestInputStream)

  if (!verifyMagicNumber(input)) {
    throw IllegalStateException("Magic number did not match")
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
      HeaderType.COMMENT -> getByteArray(input)
      HeaderType.CIPHER_ID -> kdbxHeader.setCipherUuid(getByteArray(input))
      HeaderType.COMPRESSION_FLAGS -> kdbxHeader.setCompressionFlags(getInt(input))
      HeaderType.MASTER_SEED -> kdbxHeader.masterSeed = getByteArray(input)
      HeaderType.TRANSFORM_SEED -> kdbxHeader.transformSeed = getByteArray(input)
      HeaderType.TRANSFORM_ROUNDS -> kdbxHeader.transformRounds = getLong(input)
      HeaderType.ENCRYPTION_IV -> kdbxHeader.encryptionIv = getByteArray(input)
      HeaderType.PROTECTED_STREAM_KEY -> kdbxHeader.protectedStreamKey = getByteArray(input)
      HeaderType.STREAM_START_BYTES -> kdbxHeader.streamStartBytes = getByteArray(input)
      HeaderType.INNER_RANDOM_STREAM_ID -> kdbxHeader.setInnerRandomStreamId(getInt(input))

      else -> throw IllegalStateException("Unknown File Header")
    }
  }

  // consume length etc. following END flag
  getByteArray(input)

  kdbxHeader.headerHash = digest.digest()
  return kdbxHeader
}

/**
 * Write a KdbxHeader to the output stream supplied. The header is updated with the
 * message digest of the written stream.

 * @param kdbxHeader   the header to write and update
 * @param outputStream the output stream
 */
internal fun writeKdbxHeader(kdbxHeader: KdbxHeader, outputStream: OutputStream) {
  val messageDigest = sha256MessageDigest()
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
  bb.putLong(kdbxHeader.cipherUuid.mostSignificantBits)
  bb.putLong(8, kdbxHeader.cipherUuid.leastSignificantBits)
  output.write(b)

  output.writeByte(HeaderType.COMPRESSION_FLAGS.toInt())
  output.writeShort(4)
  output.writeInt(kdbxHeader.compressionFlags.ordinal)

  output.writeByte(HeaderType.MASTER_SEED.toInt())
  output.writeShort(kdbxHeader.masterSeed.size)
  output.write(kdbxHeader.masterSeed)

  output.writeByte(HeaderType.TRANSFORM_SEED.toInt())
  output.writeShort(kdbxHeader.transformSeed.size)
  output.write(kdbxHeader.transformSeed)

  output.writeByte(HeaderType.TRANSFORM_ROUNDS.toInt())
  output.writeShort(8)
  output.writeLong(kdbxHeader.transformRounds)

  output.writeByte(HeaderType.ENCRYPTION_IV.toInt())
  output.writeShort(kdbxHeader.encryptionIv.size)
  output.write(kdbxHeader.encryptionIv)

  output.writeByte(HeaderType.PROTECTED_STREAM_KEY.toInt())
  output.writeShort(kdbxHeader.protectedStreamKey.size)
  output.write(kdbxHeader.protectedStreamKey)

  output.writeByte(HeaderType.STREAM_START_BYTES.toInt())
  output.writeShort(kdbxHeader.streamStartBytes.size)
  output.write(kdbxHeader.streamStartBytes)

  output.writeByte(HeaderType.INNER_RANDOM_STREAM_ID.toInt())
  output.writeShort(4)
  output.writeInt(kdbxHeader.protectedStreamAlgorithm.ordinal)

  output.writeByte(HeaderType.END.toInt())
  output.writeShort(0)

  kdbxHeader.headerHash = digestOutputStream.messageDigest.digest()
}

private fun getInt(input: LittleEndianDataInputStream): Int {
  val fieldLength = input.readShort()
  if (fieldLength.toInt() != 4) {
    throw IllegalStateException("Int required but length was $fieldLength")
  }
  return input.readInt()
}

private fun getLong(input: LittleEndianDataInputStream): Long {
  val fieldLength = input.readShort()
  if (fieldLength.toInt() != 8) {
    throw IllegalStateException("Long required but length was $fieldLength")
  }
  return input.readLong()
}

private fun getByteArray(input: LittleEndianDataInputStream): ByteArray {
  val value = ByteArray(input.readShort().toInt())
  input.readFully(value)
  return value
}
