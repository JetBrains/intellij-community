/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;

@State(name = "ErrorReportConfigurable", storages = @Storage(value = "other.xml", deprecated = true, roamingType = RoamingType.DISABLED))
class ErrorReportConfigurable implements PersistentStateComponent<ErrorReportConfigurable.State> {
  public static final String SERVICE_NAME = "IntelliJ Platform — JetBrains Account";

  static class State {
    public String ITN_LOGIN;
    public String ITN_PASSWORD_CRYPT;
  }

  public static ErrorReportConfigurable getInstance() {
    return ServiceManager.getService(ErrorReportConfigurable.class);
  }

  @Nullable
  @Override
  public State getState() {
    return new State();
  }

  @Override
  public void loadState(State state) {
    if (!StringUtil.isEmpty(state.ITN_LOGIN) || !StringUtil.isEmpty(state.ITN_PASSWORD_CRYPT)) {
      PasswordSafe.getInstance().set(new CredentialAttributes(SERVICE_NAME, state.ITN_LOGIN), new Credentials(state.ITN_LOGIN, Base64.getDecoder().decode(state.ITN_PASSWORD_CRYPT)));
    }
  }

  @Nullable
  public static Credentials getCredentials() {
    return PasswordSafe.getInstance().get(new CredentialAttributes(SERVICE_NAME));
  }
}
