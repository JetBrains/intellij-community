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

import com.intellij.credentialStore.PasswordSafeSettings.ProviderType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.layout.*
import com.intellij.layout.CCFlags.*
import com.intellij.layout.LCFlags.*
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.Messages

class PasswordSafeConfigurable(private val settings: PasswordSafeSettings) : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe", "Passwords", "reference.ide.settings.password.safe") {
  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi()
}

class PasswordSafeConfigurableUi : ConfigurableUi<PasswordSafeSettings> {
  private val saveOnDisk = RadioButton("Save on &disk")
  private val rememberPasswordsUntilClosing = RadioButton("Remember passwords &until the application is closed")

  override fun reset(settings: PasswordSafeSettings) {
    when (settings.providerType) {
      ProviderType.MEMORY_ONLY -> rememberPasswordsUntilClosing.isSelected = true
      ProviderType.MASTER_PASSWORD -> saveOnDisk.isSelected = true
      else -> throw IllegalStateException("Unknown provider type: ${settings.providerType}")
    }
  }

  override fun isModified(settings: PasswordSafeSettings) = getProviderType() != settings.providerType

  override fun apply(settings: PasswordSafeSettings) {
    settings.providerType = getProviderType()
  }

  override fun getComponent() = panel(noGrid, flowY, fillX) {
    val passwordSafe = PasswordSafe.getInstance() as PasswordSafeImpl

    buttonGroup(saveOnDisk, rememberPasswordsUntilClosing)

    if (!passwordSafe.isNativeCredentialStoreUsed)
    button("Clear Passwords", right) {
      passwordSafe.clearPasswords()
      Messages.showInfoMessage(this@panel, "Passwords were cleared", "Clear Passwords")
    }
  }

  private fun getProviderType(): ProviderType {
    if (rememberPasswordsUntilClosing.isSelected) {
      return ProviderType.MEMORY_ONLY
    }
    else {
      return ProviderType.MASTER_PASSWORD
    }
  }
}

interface PasswordSafeSettingsListener {
  fun typeChanged(oldValue: PasswordSafeSettings.ProviderType, newValue: PasswordSafeSettings.ProviderType)
}