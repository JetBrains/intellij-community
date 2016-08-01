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
package com.intellij.ide.passwordSafe.impl.providers;

import com.intellij.ide.passwordSafe.impl.PasswordSafeProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
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
   * @return the secret key to use
   */
  @NotNull
  protected abstract byte[] key();

  @Nullable
  public String getPassword(@Nullable Class requestor, @NotNull String key) {
    byte[] masterKey = key();
    byte[] encryptedPassword = getEncryptedPassword(EncryptionUtil.dbKey(masterKey, requestor, key));
    return encryptedPassword == null ? null : EncryptionUtil.decryptText(masterKey, encryptedPassword);
  }

  /**
   * Get encrypted password from database
   *
   * @param key the key to get
   * @return the encrypted password
   */
  protected abstract byte[] getEncryptedPassword(@NotNull byte[] key);

  /**
   * Get database key
   *
   * @param requestor the requestor class
   * @param key       the key to use
   * @return the key to use for map
   */
  @NotNull
  private byte[] dbKey(@Nullable Class requestor, String key) {
    return EncryptionUtil.dbKey(key(), requestor, key);
  }

  /**
   * Remove encrypted password from database
   *
   * @param key the key to remote
   */
  protected abstract void removeEncryptedPassword(byte[] key);

  public void setPassword(@Nullable Class requestor, @NotNull String key, @Nullable String value) {
    if (value == null) {
      removeEncryptedPassword(dbKey(requestor, key));
      return;
    }

    byte[] k = dbKey(requestor, key);
    byte[] ct = EncryptionUtil.encryptText(key(), value);
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
