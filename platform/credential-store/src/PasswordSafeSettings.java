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

  private ProviderType myProviderType = getDefaultProviderType();

  String keepassDb;

  @NotNull
  private static ProviderType getDefaultProviderType() {
    return SystemInfo.isWindows ? ProviderType.KEEPASS : ProviderType.KEYCHAIN;
  }

  @NotNull
  public ProviderType getProviderType() {
    return SystemInfo.isWindows && myProviderType == ProviderType.KEYCHAIN ? ProviderType.KEEPASS : myProviderType;
  }

  public void setProviderType(@NotNull ProviderType value) {
    //noinspection deprecation
    if (value == ProviderType.DO_NOT_STORE) {
      value = ProviderType.MEMORY_ONLY;
    }

    ProviderType oldValue = myProviderType;
    if (value != oldValue) {
      myProviderType = value;
      Application app = ApplicationManager.getApplication();
      if (app != null) {
        app.getMessageBus().syncPublisher(TOPIC).typeChanged(oldValue, value);
      }
    }
  }

  @Override
  @NotNull
  public State getState() {
    State s = new State();
    s.PROVIDER = myProviderType;
    if (keepassDb != null && !keepassDb.equals(PasswordSafeConfigurableKt.getDefaultKeePassDbFilePath())) {
      s.keepassDb = keepassDb;
    }
    return s;
  }

  @Override
  public void loadState(@NotNull State state) {
    setProviderType(ObjectUtils.chooseNotNull(state.PROVIDER, getDefaultProviderType()));
    keepassDb = StringUtil.nullize(state.keepassDb, true);
  }

  public static class State {
    public ProviderType PROVIDER = getDefaultProviderType();

    public String keepassDb;
    public boolean isRememberPasswordByDefault = true;
  }
}
