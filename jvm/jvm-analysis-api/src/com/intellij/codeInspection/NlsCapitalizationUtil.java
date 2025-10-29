// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

public final class NlsCapitalizationUtil {
  private static final Set<String> TITLE_CASE_LOWERCASE_WORDS = Set.of(
    "a", "an", "the",
    "and", "or", "but",
    "at", "by", "for", "from", "in", "into", "of", "off", "on", "onto", "out", "over", "to", "up", "with"
  );
  private static final Pattern PERIOD_PATTERN = Pattern.compile("\\.(?!\\s*$)");
  private static final Pattern DOUBLE_QUOTES_PATTERN = Pattern.compile("[“”\"]");
  private static final Pattern EXCLAMATION_PATTERN = Pattern.compile("!");
  private static final Pattern CONTRACTION_PATTERN = Pattern.compile("(?i)\\b(can't|won't|isn't|aren't|wasn't|weren't|hasn't|haven't|hadn't|doesn't|don't|didn't|shouldn't|wouldn't|couldn't|mightn't|mustn't)\\b(?<!Don't)");
  private static final Pattern WHITESPACE_SPLIT_PATTERN = Pattern.compile("(?<=\\s)|(?=\\s)");
  private static final Pattern PUNCTUATION_WITH_WORD_PATTERN = Pattern.compile("(^\\P{Alnum}*)([\\p{Alnum}]+)(\\P{Alnum}*$)");
  private static final Pattern LEADING_PUNCTUATION_WITH_FIRST_LETTER_PATTERN = Pattern.compile("(^\\P{Alnum}*)(\\p{Alpha})(.*)");
  private static final Pattern SPECIAL_PREFIX_PATTERN = Pattern.compile("^[.*~].*");

  public static boolean isCapitalizationSatisfied(String value, Nls.Capitalization capitalization) {
    if (StringUtil.isEmpty(value) || capitalization == Nls.Capitalization.NotSpecified) {
      return true;
    }
    return capitalization == Nls.Capitalization.Title
           ? checkTitleCapitalization(value)
           : checkSentenceCapitalization(value);
  }

  private static List<String> splitByWhitespace(String s) {
    return Arrays.stream(s.trim().split("\\s+"))
      .filter(str -> !str.isEmpty())
      .toList();
  }

  private static boolean checkTitleCapitalization(@NotNull String value) {
    List<String> words = splitByWhitespace(value);
    final int wordCount = words.size();
    if (wordCount == 0) return true;
    for (int i = 0; i < wordCount; i++) {
      String word = words.get(i);
      if (word.isEmpty()) continue;
      String cleanWord = stripPunctuation(word);
      if (cleanWord.isEmpty()) continue;
      // Check if it's a special case (like iOS, macOS)
      if (hasInternalCapitalization(cleanWord)) {
        continue;
      }
      if (i == 0 || i == wordCount - 1) {
        if (!isCapitalizedWord(cleanWord)) return false;
      }
      else {
        String lowerWord = cleanWord.toLowerCase(Locale.ENGLISH);
        if (TITLE_CASE_LOWERCASE_WORDS.contains(lowerWord)) {
          if (isCapitalizedWord(cleanWord)) return false;
        }
        else {
          if (!isCapitalizedWord(cleanWord)) return false;
        }
      }
    }
    return true;
  }

  private static boolean hasInternalCapitalization(@NotNull String word) {
    if (word.length() <= 1) return false;
    boolean hasLowerCase = false;
    boolean hasUpperCaseAfterFirst = false;
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      if (Character.isLetter(c)) {
        if (Character.isLowerCase(c)) {
          hasLowerCase = true;
        }
        else if (i > 0 && Character.isUpperCase(c)) {
          hasUpperCaseAfterFirst = true;
        }
      }
    }
    return hasLowerCase && hasUpperCaseAfterFirst;
  }

  private static boolean checkSentenceCapitalization(@NotNull String value) {
    List<String> words = StringUtil.split(value, " ");
    final int wordCount = words.size();
    if (wordCount == 0) return true;

    if (Character.isLetter(words.getFirst().charAt(0)) && !isCapitalizedWord(words.getFirst())) return false;
    if (wordCount == 1) return true;

    int capitalized = 1;
    for (int i = 1; i < wordCount; i++) {
      String word = words.get(i);
      if (isCapitalizedWord(word)) {
        // check for abbreviations like 'C', 'SQL', 'I18n'
        if (word.length() == 1 || !Character.isLowerCase(word.charAt(1))) {
          continue;
        }
        capitalized++;
      }
    }

    // "Start service"
    if (capitalized == 1 && wordCount == 2) return true;

    final double ratio = ((double)capitalized - 1) / wordCount;
    return ratio <= 0.4; // allow reasonable amount of capitalized words
  }

  private static boolean isCapitalizedWord(String word) {
    return !word.isEmpty() && Character.isLetter(word.charAt(0)) && Character.isUpperCase(word.charAt(0));
  }

  private static @NotNull String stripPunctuation(@NotNull String word) {
    int start = 0;
    int end = word.length();
    while (start < end && !Character.isLetterOrDigit(word.charAt(start))) {
      start++;
    }
    while (end > start && !Character.isLetterOrDigit(word.charAt(end - 1))) {
      end--;
    }
    return start < end ? word.substring(start, end) : "";
  }

  public static boolean checkPunctuation(@NotNull String value) {
    if (PERIOD_PATTERN.matcher(value).find()) {
      return value.endsWith(".");
    }
    if (value.endsWith(".")) {
      return false;
    }
    if (DOUBLE_QUOTES_PATTERN.matcher(value).find()) {
      return false;
    }
    if (EXCLAMATION_PATTERN.matcher(value).find()) {
      return false;
    }
    if (CONTRACTION_PATTERN.matcher(value).find()) {
      return false;
    }
    return true;
  }

  public static @NotNull String fixValue(String string, Nls.Capitalization capitalization) {
    if (capitalization == Nls.Capitalization.Title) {
      return fixTitleCapitalization(string);
    }
    else {
      return StringUtil.capitalize(StringUtil.wordsToBeginFromLowerCase(string));
    }
  }

  private static String fixTitleCapitalization(String text) {
    if (text == null || text.isBlank()) return text;
    String[] tokens = WHITESPACE_SPLIT_PATTERN.split(text);

    int firstWordIndex = -1, lastWordIndex = -1;
    for (int i = 0; i < tokens.length; i++) {
      String cleanedToken = stripPunctuation(tokens[i]);
      if (!cleanedToken.isEmpty()) {
        if (firstWordIndex == -1) firstWordIndex = i;
        lastWordIndex = i;
      }
    }
    if (firstWordIndex == -1) return text;

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      if (token.isBlank()) {
        result.append(token);
        continue;
      }
      String cleanedToken = stripPunctuation(token);
      if (cleanedToken.isEmpty()) {
        result.append(token);
        continue;
      }
      String lowercaseToken = cleanedToken.toLowerCase(Locale.ENGLISH);
      boolean isFirstWord = i == firstWordIndex;
      boolean isLastWord = i == lastWordIndex;

      if (!isFirstWord && !isLastWord && TITLE_CASE_LOWERCASE_WORDS.contains(lowercaseToken)) {
        result.append(applyLowercaseToTokenPreservingPunctuation(token, lowercaseToken));
      } else if (hasInternalCapitalization(cleanedToken)) {
        result.append(token);
      } else {
        result.append(capitalizeFirstLetter(token));
      }
    }
    return result.toString();
  }

  private static String applyLowercaseToTokenPreservingPunctuation(String token, String lowercaseWord) {
    var matcher = PUNCTUATION_WITH_WORD_PATTERN.matcher(token);
    return matcher.matches() ? matcher.group(1) + lowercaseWord + matcher.group(3) : token;
  }

  private static String capitalizeFirstLetter(String token) {
    if (token.isEmpty() || SPECIAL_PREFIX_PATTERN.matcher(token).matches()) return token;
    var matcher = LEADING_PUNCTUATION_WITH_FIRST_LETTER_PATTERN.matcher(token);
    if (matcher.matches()) {
      return matcher.group(1) + Character.toUpperCase(matcher.group(2).charAt(0)) + matcher.group(3);
    }
    return token;
  }
}
