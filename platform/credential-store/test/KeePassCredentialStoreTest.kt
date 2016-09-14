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

import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.util.*

// part of specific tests in the IcsCredentialTest
class FileCredentialStoreTest {
  // we don't use in memory fs to check real file io
  private val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(tempDirManager)

  @Test
  fun many() {
    val baseDir = tempDirManager.newPath()
    var provider = KeePassCredentialStore(baseDirectory = baseDir)

    val serviceName = UUID.randomUUID().toString()

    assertThat(baseDir).doesNotExist()
    val random = Random()
    for (i in 0..9) {
      val accountName = BigInteger(8 * 16, random).toString()
      provider.set(CredentialAttributes(serviceName, accountName), Credentials(accountName, BigInteger(8 * 16, random).toString()))
    }

    provider.save()
    provider = KeePassCredentialStore(baseDirectory = baseDir)

    provider.deleteFileStorage()

    val pdbFile = baseDir.resolve("c.kdbx")
    val pdbPwdFile = baseDir.resolve("pdb.pwd")

    assertThat(pdbFile).doesNotExist()
    assertThat(pdbPwdFile).doesNotExist()
  }

  @Test
  fun test() {
    val serviceName = UUID.randomUUID().toString()

    val baseDir = tempDirManager.newPath()
    var provider = KeePassCredentialStore(baseDirectory = baseDir)

    assertThat(baseDir).doesNotExist()
    val fooAttributes = CredentialAttributes(serviceName, "foo")
    assertThat(provider.get(fooAttributes)).isNull()

    provider.setPassword(fooAttributes, "pass")

    assertThat(baseDir).doesNotExist()

    val pdbFile = baseDir.resolve("c.kdbx")
    val pdbPwdFile = baseDir.resolve("pdb.pwd")

    provider.save()

    assertThat(pdbFile).isRegularFile()
    assertThat(pdbPwdFile).isRegularFile()

    val amAttributes = CredentialAttributes(serviceName, "am")
    provider.setPassword(amAttributes, "pass2")

    assertThat(provider.getPassword(fooAttributes)).isNull()
    assertThat(provider.getPassword(amAttributes)).isEqualTo("pass2")

    provider.setPassword(fooAttributes, null)
    assertThat(provider.get(fooAttributes)).isNull()

    provider.save()

    assertThat(pdbFile).isRegularFile()
    assertThat(pdbPwdFile).isRegularFile()

    provider = KeePassCredentialStore(baseDirectory = baseDir)

    assertThat(provider.get(fooAttributes)).isNull()
    assertThat(provider.getPassword(amAttributes)).isEqualTo("pass2")

    provider.setPassword(amAttributes, null)
    assertThat(provider.get(amAttributes)).isNull()

    provider.save()

    provider.deleteFileStorage()

    assertThat(pdbFile).doesNotExist()
    assertThat(pdbPwdFile).doesNotExist()
  }
}
