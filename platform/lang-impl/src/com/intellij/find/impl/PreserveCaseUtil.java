// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Bas Leijdekkers
 */
public final class PreserveCaseUtil {

  static String replaceWithCaseRespect(String toReplace, String foundString) {
    if (foundString.isEmpty() || toReplace.isEmpty()) return toReplace;
    StringBuilder buffer = new StringBuilder();

    char firstChar = foundString.charAt(0);
    if (Character.isUpperCase(firstChar)) {
      buffer.append(Character.toUpperCase(toReplace.charAt(0)));
    }
    else {
      buffer.append(Character.toLowerCase(toReplace.charAt(0)));
    }

    if (toReplace.length() == 1) return buffer.toString();

    if (foundString.length() == 1) {
      buffer.append(toReplace.substring(1));
      return buffer.toString();
    }

    boolean isReplacementLowercase = true;
    boolean isReplacementUppercase = true;
    for (int i = 1; i < toReplace.length(); i++) {
      char replacementChar = toReplace.charAt(i);
      if (!Character.isLetter(replacementChar)) continue;
      isReplacementLowercase &= Character.isLowerCase(replacementChar);
      isReplacementUppercase &= Character.isUpperCase(replacementChar);
      if (!isReplacementLowercase && !isReplacementUppercase) break;
    }

    boolean isTailUpper = true;
    boolean isTailLower = true;
    boolean isTailChecked = false;
    for (int i = 1; i < foundString.length(); i++) {
      char foundChar = foundString.charAt(i);
      if (!Character.isLetter(foundChar)) continue;
      isTailUpper &= Character.isUpperCase(foundChar);
      isTailLower &= Character.isLowerCase(foundChar);
      isTailChecked = true;
      if (!isTailUpper && !isTailLower) break;
    }
    if (!isTailChecked) {
      isTailUpper = Character.isLetter(firstChar) && Character.isUpperCase(firstChar);
      isTailLower = Character.isLetter(firstChar) && Character.isLowerCase(firstChar);
    }

    if (isTailUpper && (isReplacementLowercase || !isReplacementUppercase)) {
      buffer.append(StringUtil.toUpperCase(toReplace.substring(1)));
    }
    else if (isTailLower && (isReplacementLowercase || isReplacementUppercase)) {
      buffer.append(StringUtil.toLowerCase(toReplace.substring(1)));
    }
    else {
      buffer.append(toReplace.substring(1));
    }
    return buffer.toString();
  }
}
