// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class NameUtil {
  private static final int MAX_LENGTH = 40;

  private NameUtil() {}

  @NotNull
  public static List<String> nameToWordsLowerCase(@NotNull String name){
    return ContainerUtil.map(NameUtilCore.nameToWords(name), StringUtil::toLowerCase);
  }

  @NotNull
  public static String buildRegexp(@NotNull String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower) {
    return buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower, false, false);
  }

  @NotNull
  public static String buildRegexp(@NotNull String pattern,
                                   int exactPrefixLen,
                                   boolean allowToUpper,
                                   boolean allowToLower,
                                   boolean lowerCaseWords,
                                   boolean forCompletion) {
    final int eol = pattern.indexOf('\n');
    if (eol != -1) {
      pattern = pattern.substring(0, eol);
    }
    if (pattern.length() >= MAX_LENGTH) {
      pattern = pattern.substring(0, MAX_LENGTH);
    }

    @NonNls final StringBuilder buffer = new StringBuilder();
    final boolean endsWithSpace = !forCompletion && StringUtil.endsWithChar(pattern, ' ');
    if (!forCompletion) {
      pattern = pattern.trim();
    }
    exactPrefixLen = Math.min(exactPrefixLen, pattern.length());
    /*final boolean uppercaseOnly = containsOnlyUppercaseLetters(pattern.substring(exactPrefixLen));
    if (uppercaseOnly) {
      allowToLower = false;
    }*/
    boolean prevIsUppercase = false;
    if (exactPrefixLen > 0) {
      char c = pattern.charAt(exactPrefixLen - 1);
      prevIsUppercase = Character.isUpperCase(c) || Character.isDigit(c);
    }

    for (int i = 0; i != exactPrefixLen; ++i) {
      final char c = pattern.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        buffer.append(c);
      }
      else {
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c);
        //buffer.append(Integer.toHexString(c + 0x20000).substring(2));
      }
    }

    if (exactPrefixLen == 0) {
      buffer.append("_*");  // ignore leading underscores
    }

    boolean firstIdentifierLetter = exactPrefixLen == 0;
    boolean lastIsUppercase = false;
    for (int i = exactPrefixLen; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      lastIsUppercase = false;
      if (Character.isLetterOrDigit(c)) {
        prevIsUppercase = false;

        // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
        if (Character.isUpperCase(c) || Character.isDigit(c)) {
          prevIsUppercase = true;
          lastIsUppercase = true;

          buffer.append('(');

          if (!firstIdentifierLetter) {
            buffer.append("[a-z\\s0-9\\$]*");
          }

          buffer.append(c);
          if (allowToLower) {
            buffer.append('|');
            buffer.append(Character.toLowerCase(c));
          }
          if (!firstIdentifierLetter) {
            buffer.append("|[A-Za-z\\s0-9\\$]*[_-]+[");
            buffer.append(c);
            buffer.append(Character.toLowerCase(c));
            buffer.append("]");
          }
          buffer.append(')');
        }
        else if (Character.isLowerCase(c) && allowToUpper) {
          buffer.append('[');
          buffer.append(c);
          buffer.append(Character.toUpperCase(c));
          buffer.append(']');
          if (lowerCaseWords) {
            buffer.append("([a-z\\s0-9\\$]*[-_]+)?");
          }
        }
        else {
          buffer.append(c);
        }

        firstIdentifierLetter = false;
      }
      else if (c == '*') {
        buffer.append(".*");
        firstIdentifierLetter = true;
      }
      else if (c == '.') {
        if (!firstIdentifierLetter) {
          buffer.append("[a-z\\s0-9\\$]*\\.");
        }
        else {
          buffer.append("\\.");
        }
        firstIdentifierLetter = true;
      }
      else if (c == ' ') {
        buffer.append("([a-z\\s0-9\\$_-]*[\\ _-]+)+");
        firstIdentifierLetter = true;
      }
      else {
        if (c == ':' || prevIsUppercase) {
          buffer.append("[A-Za-z\\s0-9\\$]*");
        }

        firstIdentifierLetter = true;
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c);
        //buffer.append(Integer.toHexString(c + 0x20000).substring(3));
      }
    }

    if (!endsWithSpace) {
      buffer.append(".*");
    }
    else if (lastIsUppercase) {
      buffer.append("[a-z\\s0-9\\$]*");
    }

    //System.out.println("rx = " + buffer.toString());
    return buffer.toString();
  }

  @NotNull
  public static List<String> getSuggestionsByName(@NotNull String name,
                                                  @NotNull String prefix,
                                                  @NotNull String suffix,
                                                  boolean upperCaseStyle,
                                                  boolean preferLongerNames,
                                                  boolean isArray) {
    ArrayList<String> answer = new ArrayList<>();
    String[] words = NameUtilCore.nameToWords(name);

    for (int step = 0; step < words.length; step++) {
      int wordCount = preferLongerNames ? words.length - step : step + 1;

      String startWord = words[words.length - wordCount];
      char c = startWord.charAt(0);
      if( c == '_' || !Character.isJavaIdentifierStart( c ) )
      {
        continue;
      }

      answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, false) + suffix);
      answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, true) + suffix);
    }
    return answer;
  }

  @NotNull
  private static String compoundSuggestion(@NotNull String prefix,
                                           boolean upperCaseStyle,
                                           @NotNull String[] words,
                                           int wordCount,
                                           @NotNull String startWord,
                                           char c,
                                           boolean isArray,
                                           boolean skip_) {
    StringBuilder buffer = new StringBuilder();

    buffer.append(prefix);

    if (upperCaseStyle) {
      startWord = StringUtil.toUpperCase(startWord);
    }
    else {
      if (prefix.isEmpty() || StringUtil.endsWithChar(prefix, '_')) {
        startWord = StringUtil.toLowerCase(startWord);
      }
      else {
        startWord = Character.toUpperCase(c) + startWord.substring(1);
      }
    }
    buffer.append(startWord);

    for (int i = words.length - wordCount + 1; i < words.length; i++) {
      String word = words[i];
      String prevWord = words[i - 1];
      if (upperCaseStyle) {
        word = StringUtil.toUpperCase(word);
        if (prevWord.charAt(prevWord.length() - 1) != '_' && word.charAt(0) != '_') {
          word = "_" + word;
        }
      }
      else {
        if (prevWord.charAt(prevWord.length() - 1) == '_') {
          word = StringUtil.toLowerCase(word);
        }

        if (skip_) {
          if (word.equals("_")) continue;
          if (prevWord.equals("_")) {
            word = StringUtil.capitalize(word);
          }
        }
      }
      buffer.append(word);
    }

    String suggestion = buffer.toString();
    if (isArray) {
      suggestion = StringUtil.pluralize(suggestion);
      if (upperCaseStyle) {
        suggestion = StringUtil.toUpperCase(suggestion);
      }
    }
    return suggestion;
  }

  @NotNull
  public static String[] splitNameIntoWords(@NotNull String name) {
    return NameUtilCore.splitNameIntoWords(name);
  }

  @NotNull
  public static String[] nameToWords(@NotNull String name) {
    return NameUtilCore.nameToWords(name);
  }

  /**
   * @deprecated use {@link com.intellij.util.text.Matcher}
   */
  @Deprecated
  public interface Matcher {
    boolean matches(@NotNull String name);
  }

  @NotNull
  public static com.intellij.util.text.Matcher buildMatcher(@NotNull String pattern, int exactPrefixLen,
                                                            boolean allowToUpper, boolean allowToLower) {
    MatchingCaseSensitivity options = !allowToLower && !allowToUpper ? MatchingCaseSensitivity.ALL
                                                                     : exactPrefixLen > 0 ? MatchingCaseSensitivity.FIRST_LETTER
                                                                                          : MatchingCaseSensitivity.NONE;
    return buildMatcher(pattern, options);
  }

  @Deprecated
  @NotNull
  public static com.intellij.util.text.Matcher buildMatcher(@NotNull String pattern, int exactPrefixLen, boolean allowToUpper, boolean allowToLower, boolean lowerCaseWords) {
    MatchingCaseSensitivity options = !allowToLower && !allowToUpper ? MatchingCaseSensitivity.ALL : exactPrefixLen > 0 ? MatchingCaseSensitivity.FIRST_LETTER : MatchingCaseSensitivity.NONE;
    return buildMatcher(pattern, options);
  }

  public static class MatcherBuilder {
    private final String pattern;
    private String separators = "";
    private MatchingCaseSensitivity caseSensitivity = MatchingCaseSensitivity.NONE;
    private boolean typoTolerant = Registry.is("ide.completion.typo.tolerance");

    public MatcherBuilder(String pattern) {
      this.pattern = pattern;
    }

    public MatcherBuilder withCaseSensitivity(MatchingCaseSensitivity caseSensitivity) {
      this.caseSensitivity = caseSensitivity;
      return this;
    }

    public MatcherBuilder withSeparators(String separators) {
      this.separators = separators;
      return this;
    }

    public MatcherBuilder typoTolerant() {
      this.typoTolerant = true;
      return this;
    }

    public MinusculeMatcher build() {
      return typoTolerant ? FixingLayoutTypoTolerantMatcher.create(pattern, caseSensitivity, separators)
                          : new FixingLayoutMatcher(pattern, caseSensitivity, separators);
    }
  }

  @NotNull
  public static MatcherBuilder buildMatcher(@NotNull String pattern) {
    return new MatcherBuilder(pattern);
  }

  @NotNull
  public static MinusculeMatcher buildMatcher(@NotNull String pattern, @NotNull MatchingCaseSensitivity options) {
    return buildMatcher(pattern).withCaseSensitivity(options).build();
  }

  public static MinusculeMatcher buildMatcherWithFallback(@NotNull String pattern,
                                                          @NotNull String fallbackPattern,
                                                          @NotNull MatchingCaseSensitivity options) {
    return pattern.equals(fallbackPattern) ?
           buildMatcher(pattern, options) :
           new MatcherWithFallback(buildMatcher(pattern, options), buildMatcher(fallbackPattern, options));
  }

  @NotNull
  public static String capitalizeAndUnderscore(@NotNull String name) {
    return splitWords(name, '_', StringUtil::toUpperCase);
  }

  @NotNull
  public static String splitWords(@NotNull String text, char separator, @NotNull Function<? super String, String> transformWord) {
    final String[] words = NameUtilCore.nameToWords(text);
    boolean insertSeparator = false;
    final StringBuilder buf = new StringBuilder();
    for (String word : words) {
      if (!Character.isLetterOrDigit(word.charAt(0))) {
        buf.append(separator);
        insertSeparator = false;
        continue;
      }
      if (insertSeparator) {
        buf.append(separator);
      } else {
        insertSeparator = true;
      }
      buf.append(transformWord.fun(word));
    }
    return buf.toString();

  }

  public enum MatchingCaseSensitivity {
    NONE, FIRST_LETTER, ALL
  }
}
