// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

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
     assertThat(store.mainKeyFile).exists()
     assertThat(KeePassCredentialStore(store.dbFile, store.mainKeyFile).get(testCredentialAttributes)).isNull()
   }

   @Test
   fun `clear and remove if master password file doesn't exist`() {
     val store = createTestStoreWithCustomMasterKey()
     store.mainKeyFile.delete()
     val dbFile = store.dbFile
     TestKeePassFileManager(store).clear()
     assertThat(dbFile).doesNotExist()
     assertThat(store.mainKeyFile).doesNotExist()
   }

   @Test
   fun `clear and remove if master password file with incorrect master password`() {
     val store = createTestStoreWithCustomMasterKey()

     val oldMasterPasswordFile = store.mainKeyFile.parent.resolve("old.pwd")
     store.mainKeyFile.copy(oldMasterPasswordFile)
     store.setMasterKey("boo", SECURE_RANDOM_CACHE.value)
     oldMasterPasswordFile.copy(store.mainKeyFile)

     val dbFile = store.dbFile
     TestKeePassFileManager(store).clear()
     assertThat(dbFile).doesNotExist()
     assertThat(store.mainKeyFile).exists()
   }
}