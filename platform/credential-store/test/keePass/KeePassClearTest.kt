// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.com.intellij.credentialStore.keePass

import com.intellij.credentialStore.keePass.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.copy
import com.intellij.util.io.delete
import org.junit.Test

internal class KeePassClearTest : BaseKeePassFileManagerTest() {
  @Test
   fun clear() {
     val store = createTestStoreWithCustomMasterKey()
     val dbFile = store.dbFile
     TestKeePassFileManager(store).clear()
     assertThat(dbFile).exists()
     assertThat(store.masterKeyFile).exists()
     assertThat(KeePassCredentialStore(store.dbFile, store.masterKeyFile).get(testCredentialAttributes)).isNull()
   }

   @Test
   fun `clear and remove if master password file doesn't exist`() {
     val store = createTestStoreWithCustomMasterKey()
     store.masterKeyFile.delete()
     val dbFile = store.dbFile
     TestKeePassFileManager(store).clear()
     assertThat(dbFile).doesNotExist()
     assertThat(store.masterKeyFile).doesNotExist()
   }

   @Test
   fun `clear and remove if master password file with incorrect master password`() {
     val store = createTestStoreWithCustomMasterKey()

     val oldMasterPasswordFile = store.masterKeyFile.parent.resolve("old.pwd")
     store.masterKeyFile.copy(oldMasterPasswordFile)
     store.setMasterKey("boo", SECURE_RANDOM_CACHE.value)
     oldMasterPasswordFile.copy(store.masterKeyFile)

     val dbFile = store.dbFile
     TestKeePassFileManager(store).clear()
     assertThat(dbFile).doesNotExist()
     assertThat(store.masterKeyFile).exists()
   }
}