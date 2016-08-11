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
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.ide.passwordSafe.impl.providers.masterKey.windows.WindowsCryptUtils
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.setOwnerPermissions
import com.intellij.util.*
import com.intellij.util.containers.ContainerUtil
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.Key
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec

class FileCredentialStore(keyToValue: Map<String, String>? = null, baseDirectory: Path = Paths.get(PathManager.getConfigPath()), var memoryOnly: Boolean = false) : PasswordStorage, CredentialStore {
  override fun get(key: String) = getPassword(null, key)

  override fun set(key: String, password: ByteArray?) {
    val string = password?.toString(Charsets.UTF_8)
    password?.fill(0)
    setPassword(key, string)
  }

  private val db = ContainerUtil.newConcurrentMap<String, String>()

  private val dbFile = baseDirectory.resolve("pdb")
  private val masterKeyStorage = MasterKeyFileStorage(baseDirectory)

  private var encryptionSupport: EncryptionSupport? = null

  private val needToSave: AtomicBoolean

  init {
    if (keyToValue == null) {
      needToSave = AtomicBoolean(false)
      run {
        encryptionSupport = EncryptionSupport(SecretKeySpec(masterKeyStorage.get() ?: return@run, "AES"))

        val data: ByteArray
        try {
          data = encryptionSupport!!.decrypt(dbFile.readBytes())
        }
        catch (e: NoSuchFileException) {
          LOG.warn("key file exists, but db file not")
          return@run
        }

        val input = DataInputStream(data.inputStream())
        while (input.available() > 0) {
          db.put(input.readUTF(), input.readUTF())
        }
      }
    }
    else {
      needToSave = AtomicBoolean(!memoryOnly)
      db.putAll(keyToValue)
    }
  }

  @Synchronized
  fun save() {
    if (memoryOnly || !needToSave.compareAndSet(true, false)) {
      return
    }

    try {
      var encryptionSupport = encryptionSupport
      if (encryptionSupport == null) {
        val masterKey = generateAesKey()
        masterKeyStorage.set(masterKey)
        // set only if key stored successfully
        encryptionSupport = EncryptionSupport(SecretKeySpec(masterKey, "AES"))
        this.encryptionSupport = encryptionSupport
      }

      if (db.isEmpty()) {
        dbFile.delete()
        masterKeyStorage.set(null)
        return
      }

      val byteOut = BufferExposingByteArrayOutputStream()
      DataOutputStream(byteOut).use { out ->
        for ((key, value) in db) {
          out.writeUTF(key)
          out.writeUTF(value)
        }
      }

      dbFile.writeSafe(encryptionSupport.encrypt(byteOut.internalBuffer, byteOut.size()))
      dbFile.setOwnerPermissions()
    }
    catch (e: Throwable) {
      // schedule save again
      needToSave.set(true)
      LOG.error("Cannot save password database", e)
    }
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

  fun clear() {
    db.clear()
    needToSave.set(true)
  }

  override fun getPassword(requestor: Class<*>?, key: String): String? {
    val rawKey = getRawKey(key, requestor)
    var value = db.get(rawKey)
    if (value == null && (requestor != null || key.contains('/'))) {
      // try old key - as hash
      value = db.remove(toOldKey(rawKey))
      if (value != null) {
        db.put(rawKey, value)
        needToSave.set(true)
      }
    }
    return value
  }

  override fun setPassword(requestor: Class<*>?, key: String, value: String?) {
    val rawKey = getRawKey(key, requestor)
    if (value == null) {
      if (db.remove(rawKey) != null) {
        needToSave.set(true)
      }
    }
    else if (db.put(rawKey, value) != value) {
      needToSave.set(true)
    }
  }

  fun copyTo(store: PasswordStorage) {
    for ((k, v) in db) {
      store.setPassword(k, v)
    }
  }
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