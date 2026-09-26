// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.impl.TestPasswordSafeImpl
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
    doErasePassword(TestPasswordSafeImpl(settings, KeyChainCredentialStore()))
  }

  @Test fun `null username - macOs`() {
    assumeLocalMac()
    val settings = PasswordSafeSettings()
    settings.providerType = ProviderType.KEYCHAIN
    doNullUsername(TestPasswordSafeImpl(settings, KeyChainCredentialStore()))
  }
}
