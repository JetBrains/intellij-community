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

import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base Java-based provider for password safe that assumes a simple key-value storage.
 */
public abstract class BasePasswordSafeProvider extends PasswordSafeProvider {

  /**
   * <p>Get secret key for the provider.</p>
   * <p><b>NB: </b>
   *    This method may be called from the background,
   *    and it may need to ask user to enter the master password to access the database by calling
   *    {@link Application#invokeAndWait(Runnable, ModalityState) invokeAndWait()} to show a modal dialog.
   *    So make sure not to call it from the read action.
   *    Calling this method from the dispatch thread is allowed.</p>
   *
   * @param project the project to use
   * @param requestor
   * @return the secret key to use
   * @throws PasswordSafeException in case of problems with access to the password database.
   * @throws IllegalStateException if the method is called from the read action.
   */
  @NotNull
  protected abstract byte[] key(@Nullable Project project, @NotNull Class requestor) throws PasswordSafeException;

  @Nullable
  public String getPassword(@Nullable Project project, @NotNull Class requestor, String key) throws PasswordSafeException {
    byte[] k = dbKey(project, requestor, key);
    byte[] ct = getEncryptedPassword(k);
    return ct == null ? null : EncryptionUtil.decryptText(key(project, requestor), ct);
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
   * @param requestor the requestor class
   * @param key       the key to use
   * @return the key to use for map
   */
  private byte[] dbKey(@Nullable Project project, Class requestor, String key) throws PasswordSafeException {
    return EncryptionUtil.dbKey(key(project, requestor), requestor, key);
  }

  public void removePassword(@Nullable Project project, @NotNull Class requester, String key) throws PasswordSafeException {
    byte[] k = dbKey(project, requester, key);
    removeEncryptedPassword(k);
  }

  /**
   * Remove encrypted password from database
   *
   * @param key the key to remote
   */
  protected abstract void removeEncryptedPassword(byte[] key);

  public void storePassword(@Nullable Project project, @NotNull Class requestor, String key, String value) throws PasswordSafeException {
    byte[] k = dbKey(project, requestor, key);
    byte[] ct = EncryptionUtil.encryptText(key(project, requestor), value);
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
