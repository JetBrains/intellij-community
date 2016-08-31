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
package org.intellij.lang.regexp;

import java.nio.charset.Charset;

/**
 * @author Bas Leijdekkers
 */
public final class AsciiUtil {

  public static final Charset ASCII_CHARSET = Charset.forName("US-ASCII");

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
