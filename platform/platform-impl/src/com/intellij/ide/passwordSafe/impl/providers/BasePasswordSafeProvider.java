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

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BasePasswordSafeProvider implements PasswordStorage {
  /**
   * Get secret key for the provider
   */
  @NotNull
  protected abstract byte[] key();

  @Nullable
  public Credentials get(@NotNull CredentialAttributes attributes) {
    byte[] masterKey = key();
    byte[] encryptedPassword = getEncryptedPassword(EncryptionUtil.encryptKey(masterKey, EncryptionUtil.rawKey(attributes)));
    String password = encryptedPassword == null ? null : EncryptionUtil.decryptText(masterKey, encryptedPassword);
    return password == null ? null : new Credentials(attributes.getAccountName(), password);
  }

  protected abstract byte[] getEncryptedPassword(@NotNull byte[] key);

  protected abstract void removeEncryptedPassword(byte[] key);

  public final void set(@NotNull CredentialAttributes attributes, @Nullable Credentials value) {
    byte[] key = EncryptionUtil.encryptKey(key(), EncryptionUtil.rawKey(attributes));
    if (value == null) {
      removeEncryptedPassword(key);
    }
    else {
      storeEncryptedPassword(key, EncryptionUtil.encryptText(key(), value.getPassword()));
    }
  }

  protected abstract void storeEncryptedPassword(byte[] key, byte[] encryptedPassword);

  @Nullable
  @Override
  public final String getPassword(@NotNull CredentialAttributes attributes) {
    Credentials credentials = get(attributes);
    return credentials == null ? null : credentials.getPassword();
  }

  @Override
  public final void setPassword(@NotNull CredentialAttributes attributes, @Nullable String password) {
    set(attributes, new Credentials(attributes.getAccountName(), password));
  }
}
