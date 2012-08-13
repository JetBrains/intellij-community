/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

public class PasswordUtil {
  private PasswordUtil() { }

  // weak encryption just to avoid plain text passwords in text files
  public static String encodePassword(String password) {
    String result = "";
    if (password == null) return result;
    for (int i = 0; i < password.length(); i++) {
      int c = password.charAt(i);
      c ^= 0xdfaa;
      result += Integer.toHexString(c);
    }
    return result;
  }

  public static String decodePassword(String password) throws NumberFormatException {
    String result = "";
    if (password == null) return result;
    for (int i = 0; i < password.length(); i += 4) {
      String s = password.substring(i, i + 4);
      int c = Integer.parseInt(s, 16);
      c ^= 0xdfaa;
      result += new Character((char)c).charValue();
    }
    return result;
  }

  /** @deprecated use {@linkplain Messages#showPasswordDialog(String, String)} (to remove in IDEA 13) */
  @Nullable
  @SuppressWarnings("UnusedDeclaration")
  public static String requestPassword(String prompt, String title, final String defaultValue) {
    return Messages.showPasswordDialog(prompt, title);
  }
}