package com.intellij.credentialStore

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
}

