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
package com.intellij.ide.passwordSafe.impl.providers;

import com.intellij.openapi.project.Project;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;

/**
 * Base Java-based provider for password safe that assumes a simple key-value storage.
 */
public abstract class BasePasswordSafeProvider extends PasswordSafeProvider {

  /**
   * Get secret key for the provider
   *
   * @param project the project to use
   * @return the secret key to use
   */
  protected abstract SecretKey key(Project project) throws PasswordSafeException;

  /**
   * {@inheritDoc}
   */
  @Nullable
  public String getPassword(Project project, Class requester, String key) throws PasswordSafeException {
    byte[] k = dbKey(project, requester, key);
    byte[] ct = getEncryptedPassword(k);
    return ct == null ? null : EncryptionUtil.decryptText(key(project), ct);
  }

  /**
   * Get encrypted password from database
   *
   * @param key the key to get
   * @return the encrypted password
   */
  protected abstract byte[] getEncryptedPassword(byte[] key);

  /**
   * Get database key
   *
   * @param project
   * @param requester the requester class
   * @param key       the key to use
   * @return the key to use for map
   */
  private byte[] dbKey(Project project, Class requester, String key) throws PasswordSafeException {
    return EncryptionUtil.dbKey(key(project), requester, key);
  }

  /**
   * {@inheritDoc}
   */
  public void removePassword(Project project, Class requester, String key) throws PasswordSafeException {
    byte[] k = dbKey(project, requester, key);
    removeEncryptedPassword(k);
  }

  /**
   * Remove encrypted password from database
   *
   * @param key the key to remote
   */
  protected abstract void removeEncryptedPassword(byte[] key);

  /**
   * {@inheritDoc}
   */
  public void storePassword(Project project, Class requester, String key, String value) throws PasswordSafeException {
    byte[] k = dbKey(project, requester, key);
    byte[] ct = EncryptionUtil.encryptText(key(project), value);
    storeEncryptedPassword(k, ct);
  }

  /**
   * Store encrypted password in the database
   *
   * @param key               the key to store
   * @param encryptedPassword the password to store
   */
  protected abstract void storeEncryptedPassword(byte[] key, byte[] encryptedPassword);
}
