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
package com.intellij.credentialStore;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

@State(name = "PasswordSafe", storages = @Storage(value = "security.xml", roamingType = RoamingType.DISABLED))
public class PasswordSafeSettings implements PersistentStateComponent<PasswordSafeSettings.State> {
  public static final Topic<PasswordSafeSettingsListener> TOPIC = Topic.create("PasswordSafeSettingsListener", PasswordSafeSettingsListener.class);

  private ProviderType myProviderType = ProviderType.MASTER_PASSWORD;

  @NotNull
  public ProviderType getProviderType() {
    return myProviderType;
  }

  public void setProviderType(@NotNull ProviderType value) {
    //noinspection deprecation
    if (value == ProviderType.DO_NOT_STORE) {
      value = ProviderType.MEMORY_ONLY;
    }

    ProviderType oldValue = myProviderType;
    if (value != oldValue) {
      myProviderType = value;
      ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).typeChanged(oldValue, value);
    }
  }

  @NotNull
  public State getState() {
    State s = new State();
    s.PROVIDER = myProviderType;
    return s;
  }

  public void loadState(@NotNull State state) {
    setProviderType(ObjectUtils.chooseNotNull(state.PROVIDER, ProviderType.MASTER_PASSWORD));
  }

  public static class State {
    public ProviderType PROVIDER = ProviderType.MASTER_PASSWORD;
  }

  public enum ProviderType {
    @Deprecated
    DO_NOT_STORE,
    /**
     * The passwords are stored only in the memory
     */
    MEMORY_ONLY,
    /**
     * The passwords are encrypted with master password
     */
    MASTER_PASSWORD
  }
}
