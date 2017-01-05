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
package com.intellij.credentialStore.windows;

import com.intellij.openapi.util.SystemInfo;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * The test for windows crypt utilities
 */
public class WindowsCryptUtilTest {
  @Test
  public void testProtect() {
    if(SystemInfo.isWindows) {
      SecureRandom t = new SecureRandom();
      byte[] data = new byte[256];
      t.nextBytes(data);
      byte[] encrypted = WindowsCryptUtils.protect(data);
      byte[] decrypted = WindowsCryptUtils.unprotect(encrypted);
      Assert.assertArrayEquals(data, decrypted);
    }
  }
}
