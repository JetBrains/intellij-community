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

import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.KeePassDatabase
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.ide.passwordSafe.impl.providers.masterKey.windows.WindowsCryptUtils
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.setOwnerPermissions
import com.intellij.util.EncryptionSupport
import com.intellij.util.delete
import com.intellij.util.readBytes
import com.intellij.util.writeSafe
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.Key
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec

private const val GROUP_NAME = "IntelliJ Platform"

internal class FileCredentialStore(keyToValue: Map<CredentialAttributes, Credentials>? = null, baseDirectory: Path = Paths.get(PathManager.getConfigPath()), var memoryOnly: Boolean = false) : PasswordStorage, CredentialStore {
  private val db: KeePassDatabase

  private val dbFile = baseDirectory.resolve("c.kdbx")
  private val masterKeyStorage = MasterKeyFileStorage(baseDirectory)

  private val needToSave: AtomicBoolean

  init {
    if (keyToValue == null) {
      needToSave = AtomicBoolean(false)
      db = masterKeyStorage.get()?.let { loadKdbx(dbFile, KdbxPassword(it)) } ?: KeePassDatabase()
    }
    else {
      needToSave = AtomicBoolean(!memoryOnly)

      db = KeePassDatabase()
      val group = db.rootGroup.getOrCreateGroup(GROUP_NAME)
      for ((attributes, credentials) in keyToValue) {
        val entry = db.createEntry(attributes.serviceName)
        entry.userName = credentials.userName
        entry.password = credentials.password
        group.addEntry(entry)
      }
    }
  }

  @Synchronized
  fun save() {
    if (memoryOnly || !needToSave.compareAndSet(true, false) || !db.isDirty) {
      return
    }

    try {
      var masterKey = masterKeyStorage.get()
      if (masterKey == null) {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        masterKey = Base64.getEncoder().withoutPadding().encode(bytes)
        masterKeyStorage.set(masterKey)
      }

      dbFile.writeSafe { db.save(KdbxPassword(masterKey!!), it) }
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
    }
  }

  fun clear() {
    db.rootGroup.removeGroup(GROUP_NAME)
    needToSave.set(db.isDirty)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun getPassword(requestor: Class<*>, accountName: String): String? {
    @Suppress("DEPRECATION")
    val password = super<PasswordStorage>.getPassword(requestor, accountName)
    if (password == null) {
      // try old key - as hash
      val oldAttributes = toOldKey(requestor, accountName)
      val credentials = db.rootGroup.getGroup(GROUP_NAME)?.removeEntry(oldAttributes.serviceName, oldAttributes.accountName!!)
      if (credentials != null) {
        set(CredentialAttributes(requestor, accountName), Credentials(accountName, credentials.password))
        return credentials.password
      }
    }
    return password
  }

  override fun get(attributes: CredentialAttributes): Credentials? {
    val entry = db.rootGroup.getGroup(GROUP_NAME)?.getEntry(attributes.serviceName, attributes.accountName) ?: return null
    return Credentials(entry.userName, entry.password)
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    if (credentials == null) {
      db.rootGroup.getGroup(GROUP_NAME)?.removeEntry(attributes.serviceName, attributes.accountName)
    }
    else {
      db.rootGroup.getOrCreateGroup(GROUP_NAME).getOrCreateEntry(attributes.serviceName, attributes.accountName ?: credentials.userName).password = credentials.password
    }

    if (db.isDirty) {
      needToSave.set(true)
    }
  }

  fun copyTo(store: PasswordStorage) {
    val group = db.rootGroup.getGroup(GROUP_NAME) ?: return
    for (entry in group.entries) {
      val title = entry.title
      if (title != null) {
        store.set(CredentialAttributes(title, entry.userName), Credentials(entry.userName, entry.password))
      }
    }
  }
}

internal fun copyTo(from: Map<CredentialAttributes, Credentials>, store: PasswordStorage) {
  for ((k, v) in from) {
    store.set(k, v)
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