// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.inputStream
import org.bouncycastle.crypto.SkippingStreamCipher
import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.zip.GZIPInputStream

// https://gist.github.com/lgg/e6ccc6e212d18dd2ecd8a8c116fb1e45

@Throws(IncorrectMasterPasswordException::class)
internal fun loadKdbx(file: Path, credentials: KeePassCredentials): KeePassDatabase {
  return file.inputStream().buffered().use { readKeePassDatabase(credentials, it) }
}

private fun readKeePassDatabase(credentials: KeePassCredentials, inputStream: InputStream): KeePassDatabase {
  val kdbxHeader = KdbxHeader(inputStream)
  val decryptedInputStream = kdbxHeader.createDecryptedStream(credentials.key, inputStream)

  val startBytes = FileUtilRt.loadBytes(decryptedInputStream, 32)
  if (!Arrays.equals(startBytes, kdbxHeader.streamStartBytes)) {
    throw IncorrectMasterPasswordException()
  }

  var resultInputStream: InputStream = HashedBlockInputStream(decryptedInputStream)
  if (kdbxHeader.compressionFlags == KdbxHeader.CompressionFlags.GZIP) {
    resultInputStream = GZIPInputStream(resultInputStream)
  }
  val element = JDOMUtil.load(resultInputStream)
  element.getChild(KdbxDbElementNames.root)?.let { rootElement ->
    XmlProtectedValueTransformer(createSalsa20StreamCipher(kdbxHeader.protectedStreamKey)).processEntries(rootElement)
  }
  return KeePassDatabase(element)
}

internal class KdbxPassword(password: ByteArray) : KeePassCredentials {
  companion object {
    // KdbxPassword hashes value, so, it can be cleared before file write (to reduce time when master password exposed in memory)
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
  streamCipher.init(true /* doesn't matter, Salsa20 encryption and decryption is completely symmetrical */, ParametersWithIV(keyParameter, SALSA20_IV))
  return streamCipher
}