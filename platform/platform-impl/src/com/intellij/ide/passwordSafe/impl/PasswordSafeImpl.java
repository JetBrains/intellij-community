/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe.impl;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafe;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.PasswordDatabase;
import com.intellij.ide.passwordSafe.impl.providers.memory.MemoryPasswordSafe;
import com.intellij.ide.passwordSafe.impl.providers.nil.NilProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PasswordSafeImpl extends PasswordSafe {
  private static final Logger LOG = Logger.getInstance(PasswordSafeImpl.class.getName());
  private final PasswordSafeSettings mySettings;
  private final MasterKeyPasswordSafe myMasterKeyProvider;
  private final NilProvider myNilProvider;
  private final MemoryPasswordSafe myMemoryProvider;

  public PasswordSafeImpl(PasswordSafeSettings settings, PasswordDatabase database) {
    mySettings = settings;
    myMasterKeyProvider = new MasterKeyPasswordSafe(database);
    myNilProvider = new NilProvider();
    myMemoryProvider = new MemoryPasswordSafe();
  }

  /**
   * @return get currently selected provider
   */
  private PasswordSafeProvider provider() {
    PasswordSafeProvider p = null;
    switch (mySettings.getProviderType()) {
      case DO_NOT_STORE:
        p = myNilProvider;
        break;
      case MEMORY_ONLY:
        p = myMemoryProvider;
        break;
      case MASTER_PASSWORD:
        p = myMasterKeyProvider;
        break;
      default:
        LOG.error("Unknown provider type: " + mySettings.getProviderType());
    }
    if (p == null || !p.isSupported()) {
      p = myMemoryProvider;
    }
    return p;
  }

  public PasswordSafeSettings getSettings() {
    return mySettings;
  }

  @Nullable
  public String getPassword(@Nullable Project project, @NotNull Class requester, String key) throws PasswordSafeException {
    if (mySettings.getProviderType().equals(PasswordSafeSettings.ProviderType.MASTER_PASSWORD)) {
      String password = getMemoryProvider().getPassword(project, requester, key);
      if (password == null) {
        password = provider().getPassword(project, requester, key);
        if (password != null) {
          // cache the password in memory as well for easier access during the session
          getMemoryProvider().storePassword(project, requester, key, password);
        }
      }
      return password;
    }
    return provider().getPassword(project, requester, key);
  }

  public void removePassword(@Nullable Project project, @NotNull Class requester, String key) throws PasswordSafeException {
    if (mySettings.getProviderType().equals(PasswordSafeSettings.ProviderType.MASTER_PASSWORD)) {
      getMemoryProvider().removePassword(project, requester, key);
    }
    provider().removePassword(project, requester, key);
  }

  public void storePassword(@Nullable Project project, @NotNull Class requester, String key, String value) throws PasswordSafeException {
    if (mySettings.getProviderType().equals(PasswordSafeSettings.ProviderType.MASTER_PASSWORD)) {
      getMemoryProvider().storePassword(project, requester, key, value);
    }
    provider().storePassword(project, requester, key, value);
  }

  /**
   * @return get master key provider instance (used for configuration specific to this provider)
   */
  public MasterKeyPasswordSafe getMasterKeyProvider() {
    return myMasterKeyProvider;
  }

  public MemoryPasswordSafe getMemoryProvider() {
    return myMemoryProvider;
  }
}
