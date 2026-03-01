// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.EncryptionSpec
import com.intellij.credentialStore.createSecureRandom
import com.intellij.credentialStore.generateBytes
import com.intellij.credentialStore.kdbx.IncorrectMainPasswordException
import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.KeePassDatabase
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.application
import com.intellij.util.io.delete
import com.intellij.util.io.safeOutputStream
import com.intellij.util.messages.SimpleMessageBusConnection
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists

const val DB_FILE_NAME: String = "c.kdbx"

fun getDefaultDbFile(): Path = PathManager.getOriginalConfigDir().resolve(DB_FILE_NAME)
fun getDefaultMainPasswordFile(): Path = PathManager.getOriginalConfigDir().resolve(MAIN_KEY_FILE_NAME)

/**
 * preloadedMainKey [MainKey.value] will be cleared
 */
internal class KeePassCredentialStore(
  internal val dbFile: Path,
  private val mainKeyStorage: MainKeyFileStorage,
  preloadedDb: KeePassDatabase? = null
) : BaseKeePassCredentialStore(), Closeable {
  constructor(dbFile: Path, mainKeyFile: Path) : this(dbFile, MainKeyFileStorage(mainKeyFile), preloadedDb = null)

  private val isNeedToSave: AtomicBoolean
  @Volatile
  private var lastSavedTimestamp: Long = 0
  private val messageBusConnection: SimpleMessageBusConnection = application.messageBus.simpleConnect()

  override var db: KeePassDatabase = if (preloadedDb == null) {
    isNeedToSave = AtomicBoolean(false)
    if (dbFile.exists()) {
      val mainPassword = mainKeyStorage.load() ?: throw IncorrectMainPasswordException(isFileMissed = true)
      LocalFileSystem.getInstance().refreshAndFindFileByPath(dbFile.toString())
      loadKdbx(dbFile, KdbxPassword.createAndClear(mainPassword))
    }
    else {
      KeePassDatabase()
    }
  }
  else {
    isNeedToSave = AtomicBoolean(true)
    preloadedDb
  }

  init {
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.any { (it is VFileContentChangeEvent || it is VFileCreateEvent) && it.path.toNioPathOrNull() == dbFile }) {
          val currentTimestamp = Files.getLastModifiedTime(dbFile).toMillis()
          if (currentTimestamp > lastSavedTimestamp) {
            try {
              reload()
            }
            catch (e: Throwable) {
              logger<KeePassCredentialStore>().warn("Cannot reload KeePass database on external change", e)
            }
          }
        }
      }
    })
  }

  val mainKeyFile: Path
    get() = mainKeyStorage.passwordFile

  @Synchronized
  fun reload() {
    val key = mainKeyStorage.load() ?: throw IllegalStateException("Main key file is missing")
    val kdbxPassword = KdbxPassword(key)
    key.fill(0)
    db = loadKdbx(dbFile, kdbxPassword)
    isNeedToSave.set(false)
  }

  @Synchronized
  fun save(mainKeyEncryptionSpec: EncryptionSpec) {
    if (!isNeedToSave.compareAndSet(true, false) && !db.isDirty) {
      return
    }

    try {
      val secureRandom = createSecureRandom()
      val mainKey = mainKeyStorage.load()
      val kdbxPassword: KdbxPassword
      if (mainKey == null) {
        val key = generateRandomMainKey(mainKeyEncryptionSpec, secureRandom)
        kdbxPassword = KdbxPassword(key.value!!)
        mainKeyStorage.save(key)
      }
      else {
        kdbxPassword = KdbxPassword(mainKey)
        mainKey.fill(0)
      }

      dbFile.safeOutputStream().use {
        db.save(kdbxPassword, it, secureRandom)
      }
      lastSavedTimestamp = Files.getLastModifiedTime(dbFile).toMillis()
    }
    catch (e: Throwable) {
      // schedule a save again
      isNeedToSave.set(true)
      logger<KeePassCredentialStore>().error("Cannot save password database", e)
    }
  }

  @Synchronized
  fun deleteFileStorage() {
    try {
      dbFile.delete()
    }
    finally {
      mainKeyStorage.save(null)
    }
  }

  fun clear() {
    db.rootGroup.removeGroup(ROOT_GROUP_NAME)
    isNeedToSave.set(db.isDirty)
  }

  @TestOnly
  fun setMainPassword(mainKey: MainKey, secureRandom: SecureRandom) {
    // KdbxPassword hashes value, so, it can be cleared before a file write (to reduce time when main password exposed in memory)
    saveDatabase(dbFile = dbFile, db = db, mainKey = mainKey, mainKeyStorage = mainKeyStorage, secureRandom = secureRandom)
  }

  override fun markDirty() {
    isNeedToSave.set(true)
  }

  override fun close() {
    messageBusConnection.disconnect()
  }
}

class InMemoryCredentialStore : BaseKeePassCredentialStore() {
  override val db = KeePassDatabase()

  override fun markDirty() {}
}

internal fun generateRandomMainKey(mainKeyEncryptionSpec: EncryptionSpec, secureRandom: SecureRandom): MainKey {
  val bytes = secureRandom.generateBytes(512)
  return MainKey(Base64.getEncoder().withoutPadding().encode(bytes), isAutoGenerated = true, encryptionSpec = mainKeyEncryptionSpec)
}

internal fun saveDatabase(dbFile: Path, db: KeePassDatabase, mainKey: MainKey, mainKeyStorage: MainKeyFileStorage, secureRandom: SecureRandom) {
  val kdbxPassword = KdbxPassword(mainKey.value!!)
  mainKeyStorage.save(mainKey)
  dbFile.safeOutputStream().use {
    db.save(kdbxPassword, it, secureRandom)
  }
}

internal fun copyTo(from: Map<CredentialAttributes, Credentials>, store: CredentialStore) {
  for ((k, v) in from) {
    store.set(k, v)
  }
}
