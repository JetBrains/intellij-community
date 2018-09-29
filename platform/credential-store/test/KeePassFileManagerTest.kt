// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.copy
import com.intellij.util.io.delete
import org.junit.Rule
import org.junit.Test

internal class KeePassFileManagerTest {
  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun clear() {
    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    val credentialAttributes = CredentialAttributes("foo", "bar")
    store.set(credentialAttributes, Credentials("u", "p"))
    store.setMasterPassword("foo".toByteArray(Charsets.UTF_8))
    val dbFile = store.dbFile
    KeePassFileManager(store).clear(null)
    assertThat(dbFile).exists()
    assertThat(store.masterPasswordFile).exists()
    assertThat(KeePassCredentialStore(store.dbFile, store.masterPasswordFile).get(credentialAttributes)).isNull()
  }

  @Test
  fun `clear and remove if master password file doesn't exist`() {
    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    val credentialAttributes = CredentialAttributes("foo", "bar")
    store.set(credentialAttributes, Credentials("u", "p"))
    store.setMasterPassword("foo".toByteArray(Charsets.UTF_8))
    store.masterPasswordFile.delete()
    val dbFile = store.dbFile
    KeePassFileManager(store).clear(null)
    assertThat(dbFile).doesNotExist()
    assertThat(store.masterPasswordFile).doesNotExist()
  }

  @Test
  fun `clear and remove if master password file with incorrect master password`() {
    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    val credentialAttributes = CredentialAttributes("foo", "bar")
    store.set(credentialAttributes, Credentials("u", "p"))
    store.setMasterPassword("foo".toByteArray(Charsets.UTF_8))

    val oldMasterPasswordFile = store.masterPasswordFile.parent.resolve("old.pwd")
    store.masterPasswordFile.copy(oldMasterPasswordFile)
    store.setMasterPassword("boo".toByteArray(Charsets.UTF_8))
    oldMasterPasswordFile.copy(store.masterPasswordFile)

    val dbFile = store.dbFile
    KeePassFileManager(store).clear(null)
    assertThat(dbFile).doesNotExist()
    assertThat(store.masterPasswordFile).exists()
  }
}

@Suppress("TestFunctionName")
private fun KeePassFileManager(store: KeePassCredentialStore) = KeePassFileManager(store.dbFile, store.masterPasswordFile)