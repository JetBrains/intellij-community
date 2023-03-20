// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe.impl.providers.memory;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.credentialStore.OneTimeString;
import com.intellij.ide.passwordSafe.PasswordStorage;
import com.intellij.ide.passwordSafe.impl.PasswordSafeTimed;
import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.ide.passwordSafe.impl.providers.EncryptionUtil;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The provider that stores passwords in memory in encrypted from. It does not stores passwords on the disk,
 * so all passwords are forgotten after application exit. Some efforts are done to complicate retrieving passwords
 * from page file. However the passwords could be still retrieved from the memory using debugger or full memory dump.
 *
 * @deprecated potentially unsafe
 */
@Deprecated(forRemoval = true)
public class MemoryPasswordSafe implements PasswordStorage {
  /**
   * The key to use to encrypt data
   */
  private final transient AtomicReference<byte[]> key = new AtomicReference<>();
  /**
   * The password database
   */
  private final transient PasswordSafeTimed<Map<ByteArrayWrapper, byte[]>> database = new PasswordSafeTimed<>() {
    @Override
    protected Map<ByteArrayWrapper, byte[]> compute() {
      return Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    protected int getMinutesToLive() {
      return MemoryPasswordSafe.this.getMinutesToLive();
    }
  };

  protected int getMinutesToLive() {
    return Registry.intValue("passwordSafe.memorySafe.ttl");
  }

  protected byte @NotNull [] key() {
    if (key.get() == null) {
      byte[] rnd = new byte[EncryptionUtil.SECRET_KEY_SIZE_BYTES * 16];
      new SecureRandom().nextBytes(rnd);
      key.compareAndSet(null, EncryptionUtil.genKey(EncryptionUtil.hash(rnd)));
    }
    return key.get();
  }

  protected byte[] getEncryptedPassword(byte @NotNull [] key) {
    return database.get().get(new ByteArrayWrapper(key));
  }

  protected void removeEncryptedPassword(byte[] key) {
    database.get().remove(new ByteArrayWrapper(key));
  }

  protected void storeEncryptedPassword(byte[] key, byte[] encryptedPassword) {
    database.get().put(new ByteArrayWrapper(key), encryptedPassword);
  }

  public void clear() {
    database.get().clear();
  }

   @Override
   @Nullable
   public Credentials get(@NotNull CredentialAttributes attributes) {
     byte[] masterKey = key();
     byte[] encryptedPassword = getEncryptedPassword(EncryptionUtil.encryptKey(masterKey, EncryptionUtil.rawKey(attributes)));
     OneTimeString password = encryptedPassword == null ? null : EncryptionUtil.decryptText(masterKey, encryptedPassword);
     return password == null ? null : new Credentials(attributes.getUserName(), password);
   }

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
}
