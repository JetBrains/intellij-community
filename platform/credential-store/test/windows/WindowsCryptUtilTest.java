// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.windows;

import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

public class WindowsCryptUtilTest {
  @Test
  public void testProtect() {
    assumeTrue("Windows-only test", SystemInfo.isWindows);

    byte[] data = new byte[256];
    new Random().nextBytes(data);
    byte[] encrypted = WindowsCryptUtils.protect(data);
    assertFalse(Arrays.equals(data, encrypted));
    byte[] decrypted = WindowsCryptUtils.unprotect(encrypted);
    assertArrayEquals(data, decrypted);
  }
}