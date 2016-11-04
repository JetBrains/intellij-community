package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.Assertions.assertThat
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class PasswordSafeTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ApplicationRule()
  }

  private val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(tempDirManager)

  @Test
  fun `erase password - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    doErasePassword(PasswordSafeImpl(settings, KeePassCredentialStore(baseDirectory = tempDirManager.newPath())))
  }

  @Test
  fun `erase password - macOs`() {
    macTest {
      val settings = PasswordSafeSettings()
      settings.providerType = ProviderType.KEYCHAIN
      doErasePassword(PasswordSafeImpl(settings, KeyChainCredentialStore()))
    }
  }

  private fun doErasePassword(ps: PasswordSafeImpl) {
    val attributes = CredentialAttributes(randomString())
    try {
      ps.set(attributes, Credentials("a", "b"))
      ps.set(attributes, Credentials("a", null as String?))

      val saved = ps.get(attributes)!!
      assertThat(saved.userName).isEqualTo("a")
      assertThat(saved.password).isNullOrEmpty()
    }
    finally {
      ps.set(attributes, null)
    }
  }

  @Test
  fun `null username - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    doNullUsername(PasswordSafeImpl(settings, KeePassCredentialStore(baseDirectory = tempDirManager.newPath())))
  }

  @Test
  fun `null username - macOs`() {
    macTest {
      val settings = PasswordSafeSettings()
      settings.providerType = ProviderType.KEYCHAIN
      doNullUsername(PasswordSafeImpl(settings, KeyChainCredentialStore()))
    }
  }

  private fun doNullUsername(ps: PasswordSafeImpl) {
    val attributes = CredentialAttributes(randomString())
    try {
      ps.set(attributes, Credentials(null, "password"))

      val saved = ps.get(attributes)!!
      assertThat(saved.userName).isNullOrEmpty()
      assertThat(saved.password).isEqualTo("password")
    }
    finally {
      ps.set(attributes, null)
    }
  }

  @Test
  fun `overwrite credentials - KeePass`() {
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEEPASS
    val ps = PasswordSafeImpl(settings, KeePassCredentialStore(baseDirectory = tempDirManager.newPath()))

    val booleans = booleanArrayOf(true, false)
    for (old in booleans) {
      for (new in booleans) {
        for (rewriteNull in booleans) {
          checkCredentialsRewritten(ps, old, new, rewriteNull)
        }
      }
    }
  }

  fun checkCredentialsRewritten(passwordSafe: PasswordSafe,
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
    val ps = PasswordSafeImpl(settings, KeePassCredentialStore(baseDirectory = tempDirManager.newPath()))

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
}

