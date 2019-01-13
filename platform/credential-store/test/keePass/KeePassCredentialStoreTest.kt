// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.delete
import gnu.trove.THashMap
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.*

// part of specific tests in the IcsCredentialTest
class KeePassCredentialStoreTest {
  // we don't use in memory fs to check real file io
  private val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(tempDirManager)

  @Test
  fun many() {
    val baseDir = tempDirManager.newPath()
    var provider = createStore(baseDir)

    assertThat(baseDir).doesNotExist()
    for (i in 0..9) {
      val accountName = randomString()
      provider.set(CredentialAttributes(randomString(), accountName), Credentials(accountName, randomString()))
    }

    provider.save(defaultEncryptionSpec)
    provider = createStore(baseDir)

    provider.deleteFileStorage()

    val pdbFile = baseDir.resolve("c.kdbx")
    val pdbPwdFile = baseDir.resolve("pdb.pwd")

    assertThat(pdbFile).doesNotExist()
    assertThat(pdbPwdFile).doesNotExist()
  }

  @Test
  fun `custom db password`() {
    val baseDir = tempDirManager.newPath()
    var provider = createStore(baseDir)

    assertThat(baseDir).doesNotExist()
    val credentialMap = THashMap<CredentialAttributes, Credentials>()
    for (i in 0..9) {
      val accountName = randomString()
      val attributes = CredentialAttributes(randomString(), accountName)
      val credentials = Credentials(accountName, randomString())
      provider.set(attributes, credentials)
      credentialMap.put(attributes, credentials)
    }

    provider.setMasterKey("foo", createSecureRandom())

    val dbFile = baseDir.resolve(DB_FILE_NAME)
    val masterPasswordFile = baseDir.resolve(MASTER_KEY_FILE_NAME)

    assertThat(dbFile).exists()
    assertThat(masterPasswordFile).exists()

    provider = createStore(baseDir)

    fun check() {
      for ((attributes, credentials) in credentialMap) {
        assertThat(provider.get(attributes)).isEqualTo(credentials)
      }
    }

    check()

    // test if no master password file
    masterPasswordFile.delete()

    assertThatThrownBy {
      provider = createStore(baseDir)
    }.isInstanceOf(IncorrectMasterPasswordException::class.java)

    assertThat(dbFile).exists()
    assertThat(masterPasswordFile).doesNotExist()
  }

  @Test
  fun test() {
    val serviceName = randomString()

    val baseDir = tempDirManager.newPath()
    var provider = createStore(baseDir)

    assertThat(baseDir).doesNotExist()
    val fooAttributes = CredentialAttributes(serviceName, "foo")
    assertThat(provider.get(fooAttributes)).isNull()

    provider.setPassword(fooAttributes, "pass")
    assertThat(provider.getPassword(fooAttributes)).isEqualTo("pass")

    assertThat(baseDir).doesNotExist()

    val pdbFile = baseDir.resolve(DB_FILE_NAME)
    val pdbPwdFile = baseDir.resolve(MASTER_KEY_FILE_NAME)

    provider.save(defaultEncryptionSpec)
    assertThat(provider.getPassword(fooAttributes)).isEqualTo("pass")

    assertThat(pdbFile).isRegularFile()
    assertThat(pdbPwdFile).isRegularFile()

    val amAttributes = CredentialAttributes(serviceName, "am")
    provider.setPassword(amAttributes, "pass2")

    // null, because on set "should be the only credentials per service name" and so, item `foo` will be removed when `am` is set
    assertThat(provider.getPassword(fooAttributes)).isNull()
    assertThat(provider.getPassword(amAttributes)).isEqualTo("pass2")

    provider.setPassword(fooAttributes, null)
    assertThat(provider.get(fooAttributes)).isNull()

    provider.save(defaultEncryptionSpec)

    assertThat(pdbFile).isRegularFile()
    assertThat(pdbPwdFile).isRegularFile()

    provider = createStore(baseDir)

    assertThat(provider.get(fooAttributes)).isNull()
    assertThat(provider.getPassword(amAttributes)).isEqualTo("pass2")

    provider.setPassword(amAttributes, null)
    assertThat(provider.get(amAttributes)).isNull()

    provider.save(defaultEncryptionSpec)

    provider.deleteFileStorage()

    assertThat(pdbFile).doesNotExist()
    assertThat(pdbPwdFile).doesNotExist()
  }

  @Test
  fun `empty username`() {
    val provider = createStore(tempDirManager.newPath())
    val userName = ""
    val attributes = CredentialAttributes(randomString(), userName)
    provider.set(attributes, Credentials(userName, "foo"))
    assertThat(provider.get(attributes)).isNotNull
  }
}


private fun randomString() = UUID.randomUUID().toString()

// avoid this constructor in production sources to avoid m
@Suppress("TestFunctionName")
internal fun createStore(baseDir: Path): KeePassCredentialStore {
  return KeePassCredentialStore(dbFile = baseDir.resolve(DB_FILE_NAME),
                                masterKeyFile = baseDir.resolve(MASTER_KEY_FILE_NAME))
}

internal val defaultEncryptionSpec = EncryptionSpec(getDefaultEncryptionType(), null)