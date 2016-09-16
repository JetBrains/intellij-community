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
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
import com.intellij.util.io.decodeBase64

@State(name = "ErrorReportConfigurable", storages = arrayOf(Storage(value = "other.xml", deprecated = true, roamingType = RoamingType.DISABLED)))
internal class ErrorReportConfigurable : PersistentStateComponent<OldState> {
  override fun getState() = OldState()

  override fun loadState(state: OldState) {
    if (!state.ITN_LOGIN.isNullOrEmpty() || !state.ITN_PASSWORD_CRYPT.isNullOrEmpty()) {
      PasswordSafe.getInstance().set(CredentialAttributes(SERVICE_NAME, state.ITN_LOGIN), Credentials(state.ITN_LOGIN, state.ITN_PASSWORD_CRYPT!!.decodeBase64()))
    }
  }

  companion object {
    @JvmStatic
    val SERVICE_NAME = "IntelliJ Platform — JetBrains Account"

    val instance: ErrorReportConfigurable
      get() = ServiceManager.getService(ErrorReportConfigurable::class.java)

    @JvmStatic
    fun getCredentials() = PasswordSafe.getInstance().get(CredentialAttributes(SERVICE_NAME))
  }
}

internal class OldState {
  var ITN_LOGIN: String? = null
  var ITN_PASSWORD_CRYPT: String? = null
}