/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafe;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.PasswordDatabase;
import com.intellij.ide.passwordSafe.impl.providers.memory.MemoryPasswordSafe;
import com.intellij.ide.passwordSafe.impl.providers.nil.NilProvider;

/**
 * The implementation of password safe service
 */
public class PasswordSafeImpl extends PasswordSafe {
  /**
   * The logger instance
   */
  private static final Logger LOG = Logger.getInstance(PasswordSafeImpl.class.getName());
  /**
   * The current settings
   */
  private final PasswordSafeSettings mySettings;
  /**
   * The master key provider
   */
  private final MasterKeyPasswordSafe myMasterKeyProvider;
  /**
   * The nil provider
   */
  private final NilProvider myNilProvider;
  /**
   * The memory provider
   */
  private final MemoryPasswordSafe myMemoryProvider;

  /**
   * The constructor
   *
   * @param settings the settings for the password safe
   * @param database the password database
   */
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


  /**
   * @return settings for the passwords safe
   */
  public PasswordSafeSettings getSettings() {
    return mySettings;
  }


  /**
   * {@inheritDoc}
   */
  public String getPassword(Project project, Class requester, String key) throws PasswordSafeException {
    return provider().getPassword(project, requester, key);
  }

  /**
   * {@inheritDoc}
   */
  public void removePassword(Project project, Class requester, String key) throws PasswordSafeException {
    provider().removePassword(project, requester, key);
  }

  /**
   * {@inheritDoc}
   */
  public void storePassword(Project project, Class requester, String key, String value) throws PasswordSafeException {
    provider().storePassword(project, requester, key, value);
  }

  /**
   * @return get master key provider instance (used for configuration specific to this provider)
   */
  public MasterKeyPasswordSafe getMasterKeyProvider() {
    return myMasterKeyProvider;
  }
}
