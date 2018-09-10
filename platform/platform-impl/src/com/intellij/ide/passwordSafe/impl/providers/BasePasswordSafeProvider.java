// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe.impl.providers;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.credentialStore.OneTimeString;
import com.intellij.ide.passwordSafe.PasswordStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BasePasswordSafeProvider implements PasswordStorage {
  /**
   * Get secret key for the provider
   */
  @NotNull
  protected abstract byte[] key();

  @Override
  @Nullable
  public Credentials get(@NotNull CredentialAttributes attributes) {
    byte[] masterKey = key();
    byte[] encryptedPassword = getEncryptedPassword(EncryptionUtil.encryptKey(masterKey, EncryptionUtil.rawKey(attributes)));
    OneTimeString password = encryptedPassword == null ? null : EncryptionUtil.decryptText(masterKey, encryptedPassword);
    return password == null ? null : new Credentials(attributes.getUserName(), password);
  }

  protected abstract byte[] getEncryptedPassword(@NotNull byte[] key);

  protected abstract void removeEncryptedPassword(byte[] key);

  @Override
  public final void set(@NotNull CredentialAttributes attributes, @Nullable Credentials value) {
    byte[] key = EncryptionUtil.encryptKey(key(), EncryptionUtil.rawKey(attributes));
    if (value == null || value.getPassword() == null) {
      removeEncryptedPassword(key);
    }
    else {
      storeEncryptedPassword(key, EncryptionUtil.encryptText(key(), value.getPassword()));
    }
  }

  protected abstract void storeEncryptedPassword(byte[] key, byte[] encryptedPassword);
}
