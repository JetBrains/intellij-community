// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.impl.BasePasswordSafe
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test

class PasswordSafeTest {
  companion object {
    @ClassRule
    @JvmField val projectRule = ApplicationRule()
  }

  @Test fun `erase password - macOs`() {
    assumeLocalMac()
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEYCHAIN
    doErasePassword(BasePasswordSafe(settings, KeyChainCredentialStore()))
  }

  @Test fun `null username - macOs`() {
    assumeLocalMac()
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEYCHAIN
    doNullUsername(BasePasswordSafe(settings, KeyChainCredentialStore()))
  }
}
