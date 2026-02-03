// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.kdbx

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.sha2_512
import org.bouncycastle.crypto.SkippingStreamCipher
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.inputStream

// https://gist.github.com/lgg/e6ccc6e212d18dd2ecd8a8c116fb1e45

@Throws(IncorrectMainPasswordException::class)
internal fun loadKdbx(file: Path, credentials: KeePassCredentials): KeePassDatabase {
  return file.inputStream().buffered().use { readKeePassDatabase(credentials, it) }
}

private fun readKeePassDatabase(credentials: KeePassCredentials, inputStream: InputStream): KeePassDatabase {
  val kdbxHeader = KdbxHeader(inputStream)
  val decryptedInputStream = kdbxHeader.createDecryptedStream(credentials.key, inputStream)

  val startBytes = decryptedInputStream.readNBytes(32)
  if (!startBytes.contentEquals(kdbxHeader.streamStartBytes)) {
    throw IncorrectMainPasswordException()
  }

  var resultInputStream: InputStream = HashedBlockInputStream(decryptedInputStream)
  if (kdbxHeader.compressionFlags == KdbxHeader.CompressionFlags.GZIP) {
    resultInputStream = GZIPInputStream(resultInputStream)
  }
  val element = JDOMUtil.load(resultInputStream)
  element.getChild(KdbxDbElementNames.root)?.let { rootElement ->
    val streamCipher: SkippingStreamCipher = if (kdbxHeader.protectedStreamAlgorithm == KdbxHeader.ProtectedStreamAlgorithm.CHA_CHA) {
      createChaCha20StreamCipher(kdbxHeader.protectedStreamKey)
    }
    else {
      createSalsa20StreamCipher(kdbxHeader.protectedStreamKey)
    }
    XmlProtectedValueTransformer(streamCipher).processEntries(rootElement)
  }
  return KeePassDatabase(element)
}

internal class KdbxPassword(password: ByteArray) : KeePassCredentials {
  companion object {
    // KdbxPassword hashes value, so, it can be cleared before file write (to reduce time when main password exposed in memory)
    fun createAndClear(value: ByteArray): KeePassCredentials {
      val result = KdbxPassword(value)
      value.fill(0)
      return result
    }
  }

  override val key: ByteArray

  init {
    val md = DigestUtil.sha256()
    key = md.digest(md.digest(password))
  }
}

// 0xE830094B97205D2A
private val SALSA20_IV = byteArrayOf(-24, 48, 9, 75, -105, 32, 93, 42)

internal fun createSalsa20StreamCipher(key: ByteArray): SkippingStreamCipher {
  val streamCipher = Salsa20Engine()
  val keyParameter = KeyParameter(DigestUtil.sha256().digest(key))
  // `forEncryption` doesn't matter, Salsa20 encryption and decryption is completely symmetrical
  streamCipher.init(true, ParametersWithIV(keyParameter, SALSA20_IV))
  return streamCipher
}

internal fun createChaCha20StreamCipher(key: ByteArray): SkippingStreamCipher {
  val streamCipher = ChaCha7539Engine()
  val keyHash = sha2_512().digest(key)
  // For ChaCha20, the StreamKey value is hashed with SHA-512.
  // The first 32 bytes of the result are taken as the encryption key, the next 12 bytes as nonce.
  val keyParameter = KeyParameter(keyHash.copyOf(32))
  // `forEncryption` doesn't matter, ChaCha encryption and decryption is completely symmetrical
  streamCipher.init(true, ParametersWithIV(keyParameter, keyHash.copyOfRange(32, 32 + 12)))
  return streamCipher
}
