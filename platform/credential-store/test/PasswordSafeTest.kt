// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.impl.BasePasswordSafe
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class PasswordSafeTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ApplicationRule()
  }

  @Test
  fun `erase password - macOs`() {
    macTest {
      val settings = PasswordSafeSettings()
      settings.providerType = ProviderType.KEYCHAIN
      doErasePassword(BasePasswordSafe(settings, KeyChainCredentialStore()))
    }
  }

  @Test
  fun `null username - macOs`() {
    macTest {
      val settings = PasswordSafeSettings()
      settings.providerType = ProviderType.KEYCHAIN
      doNullUsername(BasePasswordSafe(settings, KeyChainCredentialStore()))
    }
  }
}

internal fun doNullUsername(ps: BasePasswordSafe) {
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

internal fun doErasePassword(ps: BasePasswordSafe) {
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