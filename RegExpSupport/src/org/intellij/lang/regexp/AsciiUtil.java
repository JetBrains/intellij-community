// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

/**
 * @author Bas Leijdekkers
 */
public final class AsciiUtil {

  private AsciiUtil() {}

  public static boolean isUpperCase(char c) {
    return c >= 'A' && c <= 'Z';
  }

  public static boolean isLowerCase(char c) {
    return c >= 'a' && c <= 'z';
  }

  public static boolean isLetter(char c) {
    return isUpperCase(c) || isLowerCase(c);
  }

  public static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  public static boolean isLetterOrDigit(char c) {
    return isLetter(c) || isDigit(c);
  }
}
