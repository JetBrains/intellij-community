// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.gpg

import com.intellij.credentialStore.LOG
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.SmartList

internal class Pgp(private val gpgTool: GpgToolWrapper = createGpg()) {
  // only keys with "Encrypt" capability are returned
  fun listKeys(): List<PgpKey> {
    val result = SmartList<PgpKey>()
    var keyId: String? = null
    var capabilities: String? = null
    for (line in StringUtilRt.convertLineSeparators(gpgTool.listSecretKeys()).splitToSequence('\n')) {
      val fields = line.splitToSequence(':').iterator()
      if (!fields.hasNext()) {
        continue
      }

      val tag = fields.next()
      when (tag) {
        "sec" -> {
          for (i in 2 until 5) {
            fields.next()
          }
          // Field 5 - KeyID
          keyId = fields.next()

          for (i in 6 until 12) {
            fields.next()
          }

          // Field 12 - Key capabilities
          capabilities = fields.next()
        }

        /*
         * There may be multiple user identities ("uid") following a single secret key ("sec").
         */
        "uid" -> {
          // a potential letter 'D' to indicate a disabled key
          // e :: Encrypt
          // the primary key has uppercase versions of the  letters to denote the usable capabilities of the entire key
          if (!capabilities!!.contains('D') && capabilities.contains('E')) {
            for (i in 2 until 10) {
              fields.next()
            }
            // Field 10 - User-ID
            // The value is quoted like a C string to avoid control characters (the colon is quoted =\x3a=).
            result.add(PgpKey(keyId!!, fields.next().replace("=\\x3a=", ":")))
          }
        }
      }
    }
    return result
  }

  fun decrypt(data: ByteArray) = gpgTool.decrypt(data)

  fun encrypt(data: ByteArray, recipient: String) = gpgTool.encrypt(data, recipient)
}

interface GpgToolWrapper {
  fun listSecretKeys(): String

  fun encrypt(data: ByteArray, recipient: String): ByteArray

  fun decrypt(data: ByteArray): ByteArray
}

internal fun createGpg(): GpgToolWrapper {
  val result = GpgToolWrapperImpl()
  try {
    result.version()
  }
  catch (e: Exception) {
    LOG.debug(e)
    return object : GpgToolWrapper {
      override fun encrypt(data: ByteArray, recipient: String) = throw UnsupportedOperationException()

      override fun decrypt(data: ByteArray) = throw UnsupportedOperationException()

      override fun listSecretKeys() = ""
    }
  }
  return result
}

internal data class PgpKey(@NlsSafe val keyId: String, @NlsSafe val userId: String)