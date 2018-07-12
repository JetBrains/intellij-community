// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.KeePassDatabase
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.credentialStore.windows.WindowsCryptUtils
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.setOwnerPermissions
import com.intellij.util.EncryptionSupport
import com.intellij.util.io.*
import java.nio.file.*
import java.security.Key
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec

private const val GROUP_NAME = SERVICE_NAME_PREFIX

internal const val DB_FILE_NAME = "c.kdbx"

private fun loadOrNewOnError(dbFile: Path, masterPassword: ByteArray): KeePassDatabase {
  if (!dbFile.exists()) {
    return KeePassDatabase()
  }

  return try {
    loadKdbx(dbFile, KdbxPassword(masterPassword))
  }
  catch (e: Throwable) {
    LOG.error(e)
    LOG.runAndLogException {
      dbFile.move(dbFile.parent.resolve("corrupted.c.kdbx"))
    }
    NOTIFICATION_MANAGER.notify("KeePass database file is corrupted, new one is created", null)
    KeePassDatabase()
  }
}

internal class KeePassCredentialStore(keyToValue: Map<CredentialAttributes, Credentials>? = null,
                                      baseDirectory: Path = Paths.get(PathManager.getConfigPath()),
                                      var memoryOnly: Boolean = false,
                                      dbFile: Path? = null,
                                      existingMasterPassword: ByteArray? = null) : PasswordStorage, CredentialStore {
  internal var dbFile: Path = dbFile ?: baseDirectory.resolve(DB_FILE_NAME)
    set(path) {
      if (field == path) {
        return
      }

      field = path
      needToSave.set(true)
      save()
    }

  private val db: KeePassDatabase

  private val masterKeyStorage by lazy { MasterKeyFileStorage(baseDirectory) }

  private val needToSave: AtomicBoolean

  init {
    if (keyToValue == null) {
      needToSave = AtomicBoolean(false)

      if (memoryOnly) {
        db = KeePassDatabase()
      }
      else {
        val masterPassword = existingMasterPassword ?: masterKeyStorage.get()
        if (masterPassword == null) {
          LOG.runAndLogException {
            if (this.dbFile.exists()) {
              val renameTo = baseDirectory.resolve("old.c.kdbx")
              LOG.warn("Credentials database file exists (${this.dbFile}), but no master password file. Moved to $renameTo")
              this.dbFile.move(renameTo)
            }
          }
          db = KeePassDatabase()
        }
        else {
          db = loadOrNewOnError(this.dbFile, masterPassword)
          if (existingMasterPassword != null) {
            masterKeyStorage.set(existingMasterPassword)
          }
        }
      }
    }
    else {
      needToSave = AtomicBoolean(!memoryOnly)

      db = KeePassDatabase()
      val group = db.rootGroup.getOrCreateGroup(GROUP_NAME)
      for ((attributes, credentials) in keyToValue) {
        val entry = db.createEntry(attributes.serviceName)
        entry.userName = credentials.userName
        entry.password = credentials.password?.let(::SecureString)
        group.addEntry(entry)
      }
    }
  }

  @Synchronized
  fun save() {
    if (memoryOnly || (!needToSave.compareAndSet(true, false) && !db.isDirty)) {
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

  override fun get(attributes: CredentialAttributes): Credentials? {
    val requestor = attributes.requestor
    val userName = attributes.userName
    val entry = db.rootGroup.getGroup(GROUP_NAME)?.getEntry(attributes.serviceName, attributes.userName)
    if (entry != null) {
      return Credentials(attributes.userName ?: entry.userName, entry.password?.get())
    }

    if (requestor == null || userName == null) {
      return null
    }

    // try old key - as hash
    val oldAttributes = toOldKey(requestor, userName)
    db.rootGroup.getGroup(GROUP_NAME)?.removeEntry(oldAttributes.serviceName, oldAttributes.userName)?.let {
      fun createCredentials() = Credentials(userName, it.password?.get())
      set(CredentialAttributes(requestor, userName), createCredentials())
      return createCredentials()
    }

    return null
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    if (credentials == null) {
      db.rootGroup.getGroup(GROUP_NAME)?.removeEntry(attributes.serviceName, attributes.userName)
    }
    else {
      val group = db.rootGroup.getOrCreateGroup(GROUP_NAME)
      // should be the only credentials per service name - find without user name
      val userName = attributes.userName ?: credentials.userName
      var entry = group.getEntry(attributes.serviceName, if (attributes.serviceName == SERVICE_NAME_PREFIX) userName else null)
      if (entry == null) {
        entry = group.getOrCreateEntry(attributes.serviceName, userName)
      }
      entry.userName = userName
      entry.password = if (attributes.isPasswordMemoryOnly || credentials.password == null) null else SecureString(credentials.password!!)
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
        store.set(CredentialAttributes(title, entry.userName), Credentials(entry.userName, entry.password?.get()))
      }
    }
  }

  fun setMasterPassword(masterPassword: ByteArray) {
    LOG.assertTrue(!memoryOnly)

    masterKeyStorage.set(masterPassword)
    dbFile.writeSafe { db.save(KdbxPassword(masterPassword), it) }
    dbFile.setOwnerPermissions()
  }
}

internal fun copyFileDatabase(path: Path, masterPassword: String, baseDirectory: Path = Paths.get(PathManager.getConfigPath())): KeePassCredentialStore {
  val dbFile = baseDirectory.resolve(DB_FILE_NAME)
  Files.copy(path, dbFile, StandardCopyOption.REPLACE_EXISTING)
  dbFile.setOwnerPermissions()
  return KeePassCredentialStore(baseDirectory = baseDirectory, dbFile = dbFile, existingMasterPassword = masterPassword.toByteArray())
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
  override fun encrypt(data: ByteArray, size: Int): ByteArray = WindowsCryptUtils.protect(super.encrypt(data, size))

  override fun decrypt(data: ByteArray): ByteArray = super.decrypt(WindowsCryptUtils.unprotect(data))
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