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
package com.intellij.ide.passwordSafe.masterKey

import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider
import com.intellij.ide.passwordSafe.impl.providers.masterKey.windows.WindowsCryptUtils
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.setOwnerPermissions
import com.intellij.util.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.Key
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.spec.SecretKeySpec

internal val LOG = Logger.getInstance(FilePasswordSafeProvider::class.java)

class FilePasswordSafeProvider @JvmOverloads constructor(keyToValue: Map<String, String>? = null, baseDirectory: Path = Paths.get(PathManager.getConfigPath()), var memoryOnly: Boolean = false) : PasswordSafeProvider()  {
  private val db = ConcurrentHashMap<String, String>()

  private val dbFile = baseDirectory.resolve("pdb")
  private val masterKeyStorage = MasterKeyFileStorage(baseDirectory)

  private var encryptionSupport: EncryptionSupport? = null

  private @Volatile var needToSave = false

  init {
    if (keyToValue == null) {
      init()
    }
    else {
      db.putAll(keyToValue)
      needToSave = true
    }
  }

  @Synchronized
  private fun init() {
    val masterKey = masterKeyStorage.get() ?: return
    encryptionSupport = EncryptionSupport(SecretKeySpec(masterKey, "AES"))

    val data: ByteArray
    try {
      data = encryptionSupport!!.decrypt(dbFile.readBytes())
    }
    catch (e: NoSuchFileException) {
      LOG.warn("key file exists, but db file not")
      return
    }

    val input = DataInputStream(data.inputStream())
    while (input.available() > 0) {
      db.put(input.readUTF(), input.readUTF())
    }
  }

  @Synchronized
  fun save() {
    if (memoryOnly || !needToSave) {
      return
    }

    if (encryptionSupport == null) {
      val masterKey = generateAesKey()
      encryptionSupport = EncryptionSupport(SecretKeySpec(masterKey, "AES"))
      masterKeyStorage.set(masterKey)
    }

    if (db.isEmpty()) {
      dbFile.delete()
      return
    }

    val byteOut = BufferExposingByteArrayOutputStream()
    DataOutputStream(byteOut).use { out ->
      for ((key, value) in db) {
        out.writeUTF(key)
        out.writeUTF(value)
      }
    }

    dbFile.writeSafe(encryptionSupport!!.encrypt(byteOut.internalBuffer, byteOut.size()))
    dbFile.setOwnerPermissions()

    needToSave = false
  }

  @Synchronized
  fun deleteFileStorage() {
    try {
      dbFile.delete()
    }
    finally {
      masterKeyStorage.set(null)
      encryptionSupport = null
    }
  }

  override fun getPassword(requestor: Class<*>?, key: String): String? {
    val rawKey = getRawKey(key, requestor)
    // try old key - as hash
    var value = db.get(rawKey)
    if (value == null) {
      value = db.remove(toOldKey(MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray())))
      if (value != null) {
        db.put(rawKey, value)
        needToSave = true
      }
    }
    return value
  }

  override fun setPassword(requestor: Class<*>?, key: String, value: String?) {
    val rawKey = getRawKey(key, requestor)
    if (value == null) {
      if (db.remove(rawKey) != null) {
        needToSave = true
      }
    }
    else if (db.put(rawKey, value) != value) {
      needToSave = true
    }
  }

  override fun getName() = "File PasswordSafe"
}

private fun getRawKey(key: String?, requestor: Class<*>?) = "${if (requestor == null) "" else "${requestor.name}/"}$key"

internal fun generate(): ByteArray {
  val bytes = ByteArray(16)
  SecureRandom().nextBytes(bytes)
  return bytes
}

interface MasterKeyStorage {
  fun get(): ByteArray?

  fun set(key: ByteArray?)
}

class WindowsEncryptionSupport(key: Key): EncryptionSupport(key) {
  override fun encrypt(data: ByteArray, size: Int) = WindowsCryptUtils.protect(super.encrypt(data, size))

  override fun decrypt(data: ByteArray) = super.decrypt(WindowsCryptUtils.unprotect(data))
}

class MasterKeyFileStorage(baseDirectory: Path) : MasterKeyStorage {
  private val encryptionSupport: EncryptionSupport
  private val passwordFile = baseDirectory.resolve("pdb.pwd")

  init {
    val key = SecretKeySpec(byteArrayOf(
        0x50, 0x72, 0x6f.toByte(), 0x78.toByte(), 0x79.toByte(), 0x20.toByte(),
        0x43.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x66.toByte(), 0x69.toByte(), 0x67.toByte(),
        0x20.toByte(), 0x53.toByte(), 0x65.toByte(), 0x63.toByte()), "AES")

    encryptionSupport = if (SystemInfo.isWindows) WindowsEncryptionSupport(key) else EncryptionSupport(key)
  }

  override fun get(): ByteArray? {
    val data: ByteArray
    try {
      data = passwordFile.readBytes()
    }
    catch (e: NoSuchFileException) {
      return null
    }

    try {
      return encryptionSupport.decrypt(data)
    }
    catch (e: Exception) {
      LOG.warn("Cannot decrypt master key, file content: ${Base64.getEncoder().encodeToString(data)}", e)
      return null
    }
  }

  override fun set(key: ByteArray?) {
    if (key == null) {
      passwordFile.delete()
      return
    }

    passwordFile.writeSafe(encryptionSupport.encrypt(key))
    passwordFile.setOwnerPermissions()
  }
}