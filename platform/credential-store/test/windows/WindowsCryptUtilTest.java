// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.windows;

import com.intellij.openapi.util.SystemInfo;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

import static com.intellij.credentialStore.CredentialStoreKt.createSecureRandom;

/**
 * The test for windows crypt utilities
 */
public class WindowsCryptUtilTest {
  @Test
  public void testProtect() {
    if(SystemInfo.isWindows) {
      SecureRandom t = createSecureRandom();
      byte[] data = new byte[256];
      t.nextBytes(data);
      byte[] encrypted = WindowsCryptUtils.protect(data);
      byte[] decrypted = WindowsCryptUtils.unprotect(encrypted);
      Assert.assertArrayEquals(data, decrypted);
    }
  }
}
