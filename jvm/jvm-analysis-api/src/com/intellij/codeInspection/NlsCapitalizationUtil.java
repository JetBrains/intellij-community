// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NlsCapitalizationUtil {

  public static boolean isCapitalizationSatisfied(String value, Nls.Capitalization capitalization) {
    if (StringUtil.isEmpty(value) || capitalization == Nls.Capitalization.NotSpecified) {
      return true;
    }
    value = value.replace("&", "");
    return capitalization == Nls.Capitalization.Title
           ? StringUtil.wordsToBeginFromUpperCase(value).equals(value)
           : checkSentenceCapitalization(value);
  }

  private static boolean checkSentenceCapitalization(@NotNull String value) {
    List<String> words = StringUtil.split(value, " ");
    if (words.size() == 0) return true;
    if (Character.isLetter(words.get(0).charAt(0)) && !isCapitalizedWord(words.get(0))) return false;
    if (words.size() == 1) return true;
    int capitalized = 1;
    for (int i = 1, size = words.size(); i < size; i++) {
      String word = words.get(i);
      if (isCapitalizedWord(word)) {
        // check for abbreviations like SQL or I18n
        if (word.length() == 1 || !Character.isLowerCase(word.charAt(1))) {
          continue;
        }
        capitalized++;
      }
    }
    return capitalized / words.size() < 0.2; // allow reasonable amount of capitalized words
  }

  private static boolean isCapitalizedWord(String word) {
    return word.length() > 0 && Character.isLetter(word.charAt(0)) && Character.isUpperCase(word.charAt(0));
  }

  @NotNull
  public static String fixValue(String string, Nls.Capitalization capitalization) {
    return capitalization == Nls.Capitalization.Title
           ? StringUtil.wordsToBeginFromUpperCase(string)
           : StringUtil.capitalize(StringUtil.wordsToBeginFromLowerCase(string));
  }
}
