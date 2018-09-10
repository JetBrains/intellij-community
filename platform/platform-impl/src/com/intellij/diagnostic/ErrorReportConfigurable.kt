// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.io.decodeBase64

@State(name = "ErrorReportConfigurable", storages = [(Storage(value = "other.xml", deprecated = true, roamingType = RoamingType.DISABLED))])
internal class ErrorReportConfigurable : PersistentStateComponent<OldState> {
  override fun getState() = OldState()

  override fun loadState(state: OldState) {
    if (!state.ITN_LOGIN.isNullOrEmpty() || !state.ITN_PASSWORD_CRYPT.isNullOrEmpty()) {
      PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, state.ITN_LOGIN), Credentials(state.ITN_LOGIN, state.ITN_PASSWORD_CRYPT!!.decodeBase64()))
    }
  }

  companion object {
    @JvmStatic
    val SERVICE_NAME = "$SERVICE_NAME_PREFIX — JetBrains Account"

    @JvmStatic
    fun getCredentials() = PasswordSafe.instance.get(CredentialAttributes(SERVICE_NAME))
  }
}

internal class OldState {
  var ITN_LOGIN: String? = null
  var ITN_PASSWORD_CRYPT: String? = null
}