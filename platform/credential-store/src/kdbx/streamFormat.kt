package com.intellij.credentialStore.kdbx

import org.linguafranca.pwdb.kdbx.KdbxHeader
import org.linguafranca.pwdb.kdbx.KdbxSerializer
import org.linguafranca.pwdb.kdbx.Salsa20Encryption
import org.linguafranca.pwdb.kdbx.SerializableDatabase
import java.io.InputStream
import java.io.OutputStream

interface StreamFormat {
  /**
   * Class allows for serializing a database directly to or from a stream with no encryption etc
   */
  class None : StreamFormat {
    override fun load(db: SerializableDatabase, credentials: KeePassCredentials, inputStream: InputStream) {
      db.load(inputStream)
    }

    override fun save(db: SerializableDatabase, credentials: KeePassCredentials, outputStream: OutputStream) {
      db.save(outputStream)
    }
  }

  fun load(db: SerializableDatabase, credentials: KeePassCredentials, inputStream: InputStream)

  fun save(db: SerializableDatabase, credentials: KeePassCredentials, outputStream: OutputStream)
}

class KdbxStreamFormat : StreamFormat {
  override fun load(db: SerializableDatabase, credentials: KeePassCredentials, inputStream: InputStream) {
    val kdbxHeader = KdbxHeader()
    KdbxSerializer.createUnencryptedInputStream(credentials, kdbxHeader, inputStream).use {
      db.encryption = Salsa20Encryption(kdbxHeader.protectedStreamKey)
      db.load(it)
    }
  }

  override fun save(db: SerializableDatabase, credentials: KeePassCredentials, outputStream: OutputStream) {
    val kdbxHeader = KdbxHeader()
    KdbxSerializer.createEncryptedOutputStream(credentials, kdbxHeader, outputStream).use {
      db.headerHash = kdbxHeader.headerHash
      db.encryption = Salsa20Encryption(kdbxHeader.protectedStreamKey)
      db.save(it)
    }
  }
}