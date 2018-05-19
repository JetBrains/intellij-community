// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

@State(name = "PasswordSafe", storages = @Storage(value = "security.xml", roamingType = RoamingType.DISABLED))
public class PasswordSafeSettings implements PersistentStateComponent<PasswordSafeSettings.State> {
  public static final Topic<PasswordSafeSettingsListener> TOPIC = Topic.create("PasswordSafeSettingsListener", PasswordSafeSettingsListener.class);

  private State state = new State();

  @NotNull
  private static ProviderType getDefaultProviderType() {
    return SystemInfo.isWindows ? ProviderType.KEEPASS : ProviderType.KEYCHAIN;
  }

  @NotNull
  public ProviderType getProviderType() {
    return SystemInfo.isWindows && state.PROVIDER == ProviderType.KEYCHAIN ? ProviderType.KEEPASS : state.PROVIDER;
  }

  public void setProviderType(@NotNull ProviderType value) {
    //noinspection deprecation
    if (value == ProviderType.DO_NOT_STORE) {
      value = ProviderType.MEMORY_ONLY;
    }

    ProviderType oldValue = state.PROVIDER;
    if (value != oldValue) {
      state.PROVIDER = value;
      Application app = ApplicationManager.getApplication();
      if (app != null) {
        app.getMessageBus().syncPublisher(TOPIC).typeChanged(oldValue, value);
      }
    }
  }

  @Override
  @NotNull
  public State getState() {
    if (state.keepassDb != null && state.keepassDb.equals(PasswordSafeConfigurableKt.getDefaultKeePassDbFilePath())) {
      state.keepassDb = null;
    }
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
    setProviderType(ObjectUtils.chooseNotNull(state.PROVIDER, getDefaultProviderType()));
    state.keepassDb = StringUtil.nullize(state.keepassDb, true);
  }

  public static class State {
    public ProviderType PROVIDER = getDefaultProviderType();

    public String keepassDb;
    public boolean isRememberPasswordByDefault = true;
  }
}
