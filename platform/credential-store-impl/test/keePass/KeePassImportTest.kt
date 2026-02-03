// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.delete
import com.intellij.util.io.move
import org.junit.Test

internal class KeePassImportTest : BaseKeePassFileManagerTest() {
  @Test
  fun `import with custom master key located under imported file dir`() {
    val otherStore = createTestStoreWithCustomMasterKey(fsRule.fs.getPath("/other"))
    otherStore.save(defaultEncryptionSpec)

    val store = createStore()
    TestKeePassFileManager(store).import(otherStore.dbFile, event = null)

    checkStoreAfterSuccessfulImport(store)
  }

  @Test
  fun `import with custom master key but key file doesn't exist`() {
    val otherStore = createTestStoreWithCustomMasterKey(fsRule.fs.getPath("/other"))
    otherStore.save(defaultEncryptionSpec)
    fsRule.fs.getPath("/other/$MAIN_KEY_FILE_NAME").move(fsRule.fs.getPath("/other/otherKey"))

    val store = createStore()
    val keePassFileManager = TestKeePassFileManager(store)
    keePassFileManager.import(otherStore.dbFile, event = null)
    assertThat(keePassFileManager.isUnsatisfiedMasterPasswordRequest).isTrue()
    assertThat(store.dbFile).doesNotExist()
    assertThat(store.mainKeyFile).doesNotExist()

    // assert that other store not corrupted
    fsRule.fs.getPath("/other/otherKey").move(fsRule.fs.getPath("/other/$MAIN_KEY_FILE_NAME"))
    otherStore.reload()
    assertThat(otherStore.get(testCredentialAttributes)!!.password!!.toString()).isEqualTo("p")
  }

  @Test
  fun `import with custom master key but key file doesn't exist but user provided new`() {
    val otherStore = createTestStoreWithCustomMasterKey(fsRule.fs.getPath("/other"))
    otherStore.save(defaultEncryptionSpec)
    fsRule.fs.getPath("/other/$MAIN_KEY_FILE_NAME").delete()

    val store = createStore()
    val keePassFileManager = TestKeePassFileManager(store, masterPasswordRequestAnswer = "foo")
    keePassFileManager.import(otherStore.dbFile, event = null)
    assertThat(keePassFileManager.isUnsatisfiedMasterPasswordRequest).isFalse()
    checkStoreAfterSuccessfulImport(store)
  }
}