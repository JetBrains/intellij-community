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
package com.intellij.ide.passwordSafe.impl.providers.memory;

import com.intellij.ide.passwordSafe.impl.PasswordSafeTimed;
import com.intellij.ide.passwordSafe.impl.providers.BasePasswordSafeProvider;
import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.ide.passwordSafe.impl.providers.EncryptionUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The provider that stores passwords in memory in encrypted from. It does not stores passwords on the disk,
 * so all passwords are forgotten after application exit. Some efforts are done to complicate retrieving passwords
 * from page file. However the passwords could be still retrieved from the memory using debugger or full memory dump.
 */
@Deprecated
// used in https://github.com/groboclown/p4ic4idea, cannot be deleted
public class MemoryPasswordSafe extends BasePasswordSafeProvider {
  /**
   * The key to use to encrypt data
   */
  private final transient AtomicReference<byte[]> key = new AtomicReference<>();
  /**
   * The password database
   */
  private final transient PasswordSafeTimed<Map<ByteArrayWrapper, byte[]>> database = new PasswordSafeTimed<Map<ByteArrayWrapper, byte[]>>() {
    @Override
    protected Map<ByteArrayWrapper, byte[]> compute() {
      return Collections.synchronizedMap(ContainerUtil.<ByteArrayWrapper, byte[]>newHashMap());
    }

    @Override
    protected int getMinutesToLive() {
      return MemoryPasswordSafe.this.getMinutesToLive();
    }
  };

  protected int getMinutesToLive() {
    return Registry.intValue("passwordSafe.memorySafe.ttl");
  }

  @NotNull
  @Override
  protected byte[] key() {
    if (key.get() == null) {
      byte[] rnd = new byte[EncryptionUtil.SECRET_KEY_SIZE_BYTES * 16];
      new SecureRandom().nextBytes(rnd);
      key.compareAndSet(null, EncryptionUtil.genKey(EncryptionUtil.hash(rnd)));
    }
    return key.get();
  }

  @Override
  protected byte[] getEncryptedPassword(@NotNull byte[] key) {
    return database.get().get(new ByteArrayWrapper(key));
  }

  @Override
  protected void removeEncryptedPassword(byte[] key) {
    database.get().remove(new ByteArrayWrapper(key));
  }

  @Override
  protected void storeEncryptedPassword(byte[] key, byte[] encryptedPassword) {
    database.get().put(new ByteArrayWrapper(key), encryptedPassword);
  }

  public void clear() {
    database.get().clear();
  }
}
