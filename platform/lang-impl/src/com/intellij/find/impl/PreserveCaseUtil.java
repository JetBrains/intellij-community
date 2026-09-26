// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;
import java.util.Locale;

@ApiStatus.Internal
public final class PreserveCaseUtil {
  private PreserveCaseUtil() {
  }

  @VisibleForTesting
  public static String replaceWithCaseRespect(String toReplace, String foundString) {
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

  /**
   * Applies the case of the found string on the replacement string. This method understands
   * UPPER_CASE, lower_case and Title_Case, and APPLIES casing PER Word.
   *
   * @param found  the string from which to use the case.
   * @param replacement  the string to apply the case to
   * @return the replacement string with the case of the found string.
   */
  public static String applyCase(@NonNls String found, @NonNls String replacement) {
    if (found.isEmpty() || replacement.isEmpty()) return replacement;

    List<String> words = collectWords(found);
    if (words.isEmpty()) {
      return replacement;
    }
    StringBuilder result = new StringBuilder();
    int index = 0;
    int start = -1;
    for (int i = 0, length = replacement.length(); i < length; i++) {
      final char c = replacement.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (start < 0) {
          start = i;
        }
      }
      else {
        if (start >= 0) {
          buildWord(replacement.substring(start, i), analyze(words.get(index)), result);
          start = -1;
          if (index < words.size() - 1) index++;
        }
        result.append(c);
      }
    }
    if (start >= 0) {
      buildWord(replacement.substring(start), analyze(words.get(index)), result);
    }
    return result.toString();
  }

  private static WordCase analyze(String word) {
    boolean upperCase = true;
    boolean lowerCase = true;
    boolean capitalized = true;
    for (int i = 0, length = word.length(); i < length; i++) {
      final char c = word.charAt(i);
      if (Character.isLetter(c)) { // assume no digit at the start of identifier
        final boolean u = Character.isUpperCase(c);
        final boolean l = Character.isLowerCase(c);
        if (upperCase && lowerCase) {
          capitalized = u;
        }
        upperCase &= u;
        lowerCase &= l;
      }
    }
    return new WordCase(upperCase, lowerCase, capitalized);
  }

  private static List<String> collectWords(String string) {
    boolean prevIsWordChar = false;
    final List<String> result = new SmartList<>();
    final StringBuilder word = new StringBuilder();
    for (int i = 0, length = string.length(); i < length; i++) {
      final char c = string.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (!prevIsWordChar) {
          prevIsWordChar = true;
        }
        word.append(c);
      }
      else if (prevIsWordChar) {
        result.add(word.toString());
        word.setLength(0);
        prevIsWordChar = false;
      }
    }
    if (!word.isEmpty()) {
      result.add(word.toString());
    }
    return result;
  }

  private static void buildWord(@NonNls String word, WordCase foundCase, StringBuilder result) {
    if (foundCase.upperCase && foundCase.lowerCase) {
      // no letters seen
      result.append(word);
      return;
    }
    WordCase replacementCase = analyze(word);
    if (foundCase.upperCase) {
      if (replacementCase.upperCase) {
        result.append(word);
      }
      else {
        result.append(word.toUpperCase(Locale.getDefault()));
      }
    }
    else {
      boolean prevIsWordChar = false;
      for (int i = 0, length = word.length(); i < length; i++) {
        final char c = word.charAt(i);
        if (Character.isLetter(c)) {
          if (!prevIsWordChar) {
            result.append(foundCase.lowerCase || !foundCase.capitalized
                          ? StringUtil.toLowerCase(c)
                          : StringUtil.toUpperCase(c));
            prevIsWordChar = true;
          }
          else {
            result.append(replacementCase.upperCase ? StringUtil.toLowerCase(c) : c);
          }
        }
        else {
          prevIsWordChar = Character.isDigit(c);
          result.append(c);
        }
      }
    }
  }

  private record WordCase(boolean upperCase, boolean lowerCase, boolean capitalized) {}
}
