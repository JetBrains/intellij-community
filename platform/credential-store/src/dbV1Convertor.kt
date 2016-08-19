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

import com.intellij.ide.ApplicationLoadListener
import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper
import com.intellij.ide.passwordSafe.impl.providers.EncryptionUtil
import com.intellij.ide.passwordSafe.impl.providers.masterKey.EnterPasswordComponent
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterPasswordDialog
import com.intellij.ide.passwordSafe.impl.providers.masterKey.PasswordDatabase
import com.intellij.ide.passwordSafe.impl.providers.masterKey.windows.WindowsCryptUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.exists
import gnu.trove.THashMap
import java.nio.file.Paths
import java.util.function.Function

private val TEST_PASSWORD_VALUE = "test password"

internal fun isMasterPasswordValid(password: String, @Suppress("DEPRECATION") db: PasswordDatabase): Boolean {
  val key = EncryptionUtil.genPasswordKey(password)
  val value = db.myDatabase.get(ByteArrayWrapper(EncryptionUtil.encryptKey(key, rawTestKey(password))))
  if (value != null) {
    return EncryptionUtil.decryptText(key, value) == TEST_PASSWORD_VALUE
  }
  return false
}

internal fun checkPassAndConvertOldDb(password: String, @Suppress("DEPRECATION") db: PasswordDatabase): Map<String, String>? {
  if (isMasterPasswordValid(password, db)) {
    return convertOldDb(password, db)
  }
  else {
    return null
  }
}

fun convertOldDb(@Suppress("DEPRECATION") db: PasswordDatabase): Map<String, String>? {
  if (db.myDatabase.size <= 1) {
    return null
  }

  // trying empty password: people who have set up empty password, don't want to get disturbed by the prompt
  checkPassAndConvertOldDb("", db)?.let { return it }

  if (SystemInfo.isWindows) {
    db.myMasterPassword?.let {
      try {
        WindowsCryptUtils.unprotect(it).toString(Charsets.UTF_8)
      }
      catch (e: Exception) {
        LOG.warn(e)
        null
      }
    }?.let {
      checkPassAndConvertOldDb(it, db)?.let { return it }
    }
  }

  // if db contains only one entry (+ test entry) - do not ask master pass, just skip
  if (db.myDatabase.size <= 2) {
    return null
  }

  var result: Map<String, String>? = null
  val dialog = MasterPasswordDialog(EnterPasswordComponent(Function {
    result = checkPassAndConvertOldDb(it, db)
    result != null
  }))

  if (ApplicationManager.getApplication().isUnitTestMode) {
    dialog.doOKAction()
    dialog.close(DialogWrapper.OK_EXIT_CODE)
  }
  else if (!dialog.showAndGet()) {
    LOG.warn("User cancelled master password dialog, will be recreated")
  }
  return result
}

internal fun convertOldDb(oldKey: String, @Suppress("DEPRECATION") db: PasswordDatabase): Map<String, String> {
  val oldKeyB = EncryptionUtil.genPasswordKey(oldKey)
  val testKey = ByteArrayWrapper(EncryptionUtil.encryptKey(oldKeyB, rawTestKey(oldKey)))
  val newDb = THashMap<String, String>(db.myDatabase.size)
  for ((key, value) in db.myDatabase) {
    if (testKey == key) {
      continue
    }

    // in old db we cannot get key value - it is hashed, so, we store it as a base64 encoded in the new DB
    newDb.put(toOldKey(EncryptionUtil.decryptKey(oldKeyB, key.unwrap())), EncryptionUtil.decryptText(oldKeyB, value))
  }
  return newDb
}

private fun rawTestKey(oldKey: String) = EncryptionUtil.hash("com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafe/TEST_PASSWORD:${oldKey}".toByteArray())

internal class PasswordDatabaseConvertor : ApplicationLoadListener {
  override fun beforeComponentsCreated() {
    try {
      val oldDbFile = Paths.get(PathManager.getConfigPath(), "options", "security.xml")
      if (oldDbFile.exists()) {
        val settings = ServiceManager.getService(PasswordSafeSettings::class.java)
        if (settings.providerType != PasswordSafeSettings.ProviderType.MASTER_PASSWORD) {
          return
        }

        @Suppress("DEPRECATION")
        val oldDb = ServiceManager.getService(PasswordDatabase::class.java)
        // old db contains at least one test key - skip it
        if (oldDb.myDatabase.size > 1) {
          @Suppress("DEPRECATION")
          val newDb = convertOldDb(ServiceManager.getService<PasswordDatabase>(PasswordDatabase::class.java))
          if (newDb != null && newDb.isNotEmpty()) {
            LOG.catchAndLog {
              for (factory in CredentialStoreFactory.CREDENTIAL_STORE_FACTORY.extensions) {
                val store = factory.create() ?: continue
                for ((k, v) in newDb) {
                  store.setPassword(k, v)
                }
                return
              }
            }
            FileCredentialStore(newDb).save()
          }
        }
      }
    }
    catch (e: Throwable) {
      LOG.warn("Cannot check old password safe DB", e)
    }
  }
}