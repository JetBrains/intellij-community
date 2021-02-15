// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.BasePasswordSafe
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class PasswordSafeKeePassTest {
  companion object {
    @ClassRule
    @JvmField val projectRule = ApplicationRule()
  }

  @Rule
  @JvmField val fsRule = InMemoryFsRule()

  @Test fun `erase password - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    doErasePassword(BasePasswordSafe(settings, createKeePassStore()))
  }

  @Test fun `null username - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    doNullUsername(BasePasswordSafe(settings, createKeePassStore()))
  }

  @Test fun `overwrite credentials - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    val ps = BasePasswordSafe(settings, createKeePassStore())

    val booleans = booleanArrayOf(true, false)
    for (old in booleans) {
      for (new in booleans) {
        for (rewriteNull in booleans) {
          checkCredentialsRewritten(ps, old, new, rewriteNull)
        }
      }
    }
  }

  private fun checkCredentialsRewritten(passwordSafe: PasswordSafe,
                                        isMemoryOnlyOld: Boolean,
                                        isMemoryOnlyNew: Boolean,
                                        rewriteWithNull: Boolean) {
    val id = "test checkCredentialsRewritten $isMemoryOnlyOld $isMemoryOnlyNew $rewriteWithNull"
    val attributesMemoryOnly = CredentialAttributes(id, null, null, true)
    val attributesSaved = CredentialAttributes(id, null, null, false)

    val description = "Parameters isMemoryOnlyOld=$isMemoryOnlyOld isMemoryOnlyNew=$isMemoryOnlyNew rewriteWithNull=$rewriteWithNull"
    val oldPassword = "oldPassword"
    val newPassword = if (rewriteWithNull) null else "newPassword"
    val oldCredentials = Credentials("oldName", oldPassword)
    val newCredentials = Credentials("newName", newPassword)

    try {
      passwordSafe.set(if (isMemoryOnlyOld) attributesMemoryOnly else attributesSaved, oldCredentials)
      passwordSafe.set(if (isMemoryOnlyNew) attributesMemoryOnly else attributesSaved, newCredentials)

      checkCredentials(description, attributesMemoryOnly, newCredentials, passwordSafe, rewriteWithNull)
      checkCredentials(description, attributesSaved, newCredentials, passwordSafe, rewriteWithNull)
    }
    finally {
      passwordSafe.set(attributesMemoryOnly, null)
      passwordSafe.set(attributesSaved, null)
    }
  }

  private fun checkCredentials(description: String,
                               attributes: CredentialAttributes,
                               newCredentials: Credentials,
                               passwordSafe: PasswordSafe,
                               rewriteWithNull: Boolean) {
    val resultMemoryOnly = passwordSafe.get(attributes)!!
    assertThat(resultMemoryOnly.userName).`as`(description).isEqualTo(newCredentials.userName)
    val assertThat = assertThat(resultMemoryOnly.password).`as`(description)
    if (rewriteWithNull) {
      assertThat.isNullOrEmpty()
    }
    else {
      assertThat.isEqualTo(newCredentials.password)
    }
  }

  @Test
  fun `credentials with empty username - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    val ps = BasePasswordSafe(settings, createKeePassStore())

    val id = "test PasswordSafeTest.credentials with empty username"
    val attributes = CredentialAttributes(id, isPasswordMemoryOnly = true)
    try {
      val credentials = Credentials(null, "passphrase")
      ps.set(attributes, credentials)
      val saved = ps.get(attributes)!!
      assertThat(saved.password).isEqualTo(credentials.password)
      assertThat(saved.userName).isNullOrEmpty()
    }
    finally {
      ps.set(attributes, null)
    }
  }

  private fun createKeePassStore() = createStore(fsRule.fs.getPath("/"))
}
