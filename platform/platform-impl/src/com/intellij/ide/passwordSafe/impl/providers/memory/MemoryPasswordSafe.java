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
package com.intellij.ide.passwordSafe.impl.providers.memory;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.intellij.ide.passwordSafe.impl.providers.BasePasswordSafeProvider;
import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.ide.passwordSafe.impl.providers.EncryptionUtil;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The provider that stores passwords in memory in encrypted from. It does not stores passwords on the disk,
 * so all passwords are forgotten after application exit. Some efforts are done to complicate retrieving passwords
 * from page file. However the passwords could be still retrieved from the memory using debugger or full memory dump.
 */
public class MemoryPasswordSafe extends BasePasswordSafeProvider {
  /**
   * The key to use to encrypt data
   */
  private transient final AtomicReference<SecretKey> key = new AtomicReference<SecretKey>();
  /**
   * The password database
   */
  private transient final Map<ByteArrayWrapper, byte[]> database = new HashMap<ByteArrayWrapper, byte[]>();

  /**
   * @param project the project to use
   * @return the secret key used by provider
   */
  @Override
  protected SecretKey key(Project project) {
    if (key.get() == null) {
      key.compareAndSet(null, EncryptionUtil.genKey(new SecureRandom()));
    }
    return key.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected byte[] getEncryptedPassword(byte[] key) {
    synchronized (database) {
      return database.get(new ByteArrayWrapper(key));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void removeEncryptedPassword(byte[] key) {
    synchronized (database) {
      database.remove(new ByteArrayWrapper(key));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void storeEncryptedPassword(byte[] key, byte[] encryptedPassword) {
    synchronized (database) {
      database.put(new ByteArrayWrapper(key), encryptedPassword);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSupported() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDescription() {
    return "Memory-based password safe provider. The passwords are stored only for the duration of IDEA process.";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return "Memory PasswordSafe";
  }
}
