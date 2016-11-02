package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.Assertions.assertThat
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
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
    val ps = PasswordSafeImpl(settings, KeePassCredentialStore(baseDirectory = tempDirManager.newPath()))

    val attributes = CredentialAttributes("aaa", null, null, false)
    ps.set(attributes, Credentials("a", "b"))
    ps.set(attributes, Credentials("a", null as String?))

    val saved = ps.get(attributes)!!
    assertThat(saved.userName).isEqualTo("a")
    assertThat(saved.password).isNullOrEmpty()
  }

  @Test
  fun `erase password - macOs`() {
    if (!(SystemInfo.isMacIntel64 && !UsefulTestCase.IS_UNDER_TEAMCITY)) {
      return
    }

    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEYCHAIN
    val ps = PasswordSafeImpl(settings, KeyChainCredentialStore())

    val attributes = CredentialAttributes("aaa", null, null, false)
    ps.set(attributes, Credentials("a", "b"))
    ps.set(attributes, Credentials("a", null as String?))

    val saved = ps.get(attributes)!!
    assertThat(saved.userName).isEqualTo("a")
    assertThat(saved.password).isNullOrEmpty()
  }
}

