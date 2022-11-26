// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.impl;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a replacement string for search/replace operation using regular expressions.
 * <p>
 * The logic is based on java.util.regex.Matcher.appendReplacement method, special characters (\n, \r, \t, \f, \b, \xNNNN)
 * and case conversion characters (\l, &#92;u, \L, \U, \E) are additionally supported.
 * <p>
 * Instances of this class are not safe for use by multiple concurrent threads, just as {@link Matcher} instances are.
 */
public class RegExReplacementBuilder {
  @NotNull private final MatchGroupContainer myMatcher;

  private String myTemplate;
  private int myCursor;
  private StringBuilder myReplacement;
  private List<CaseConversionRegion> myConversionRegions;

  public RegExReplacementBuilder(@NotNull Matcher matcher) {
    myMatcher = new MatchGroupContainer() {
      @Override
      public String group(String name) {
        return matcher.group(name);
      }

      @Override
      public String group(int num) {
        return matcher.group(num);
      }

      @Override
      public int groupCount() {
        return matcher.groupCount();
      }
    };
  }

  private RegExReplacementBuilder(@NotNull Pattern pattern) {
    myMatcher = new MatchGroupContainer() {
      @Override
      public String group(String name) {
        return "";
      }

      @Override
      public String group(int group) {
        if (group < 0 || group > groupCount())
          throw new IllegalArgumentException("No group " + group);
        return "";
      }

      @Override
      public int groupCount() {
        return pattern.matcher("").groupCount();
      }
    };
  }

  /**
   * Validates the replacement template. This doesn't check currently whether group names actually exist.
   * @param pattern current pattern
   * @param template replacement template
   * @throws IllegalArgumentException if template is malformed
   */
  public static void validate(Pattern pattern, String template) throws IllegalArgumentException {
    new RegExReplacementBuilder(pattern).createReplacement(template);
  }

  /**
   * Generates a replacement string from provided template value, substituting referenced capturing group values, and processing supported
   * special and control characters.
   * <p>
   * Matcher used to create this instance of RegExReplacementBuilder is supposed to be in a state
   * created by a successful {@link Matcher#find() find()} or {@link Matcher#find(int) find(int)} invocation.
   */
  public String createReplacement(String template) {
    myTemplate = template;
    resetState();
    while (myCursor < myTemplate.length()) {
      char nextChar = myTemplate.charAt(myCursor++);
      if (nextChar == '\\') {
        processEscapedChar();
      } else if (nextChar == '$') {
        processGroupValue();
      } else {
        myReplacement.append(nextChar);
      }
    }
    return generateResult();
  }

  private void resetState() {
    myCursor = 0;
    myReplacement = new StringBuilder();
    myConversionRegions = new ArrayList<>();
  }

  private void processEscapedChar() {
    char nextChar;
    if (myCursor == myTemplate.length()) throw new IllegalArgumentException("character to be escaped is missing");
    nextChar = myTemplate.charAt(myCursor++);
    switch (nextChar) {
      case 'n' -> myReplacement.append('\n');
      case 'r' -> myReplacement.append('\r');
      case 'b' -> myReplacement.append('\b');
      case 't' -> myReplacement.append('\t');
      case 'f' -> myReplacement.append('\f');
      case 'x' -> {
        if (myCursor + 4 <= myTemplate.length()) {
          try {
            int code = Integer.parseInt(myTemplate.substring(myCursor, myCursor + 4), 16);
            myCursor += 4;
            myReplacement.append((char)code);
          }
          catch (NumberFormatException ignored) {}
        }
      }
      case 'l' -> startConversionForCharacter(false);
      case 'u' -> startConversionForCharacter(true);
      case 'L' -> startConversionForRegion(false);
      case 'U' -> startConversionForRegion(true);
      case 'E' -> resetConversionState();
      default -> myReplacement.append(nextChar);
    }
  }

  private void processGroupValue() {
    char nextChar;
    if (myCursor == myTemplate.length()) throw new IllegalArgumentException("Illegal group reference: group index is missing");
    nextChar = myTemplate.charAt(myCursor++);
    String group;
    if (nextChar == '{') {
      StringBuilder gsb = new StringBuilder();
      while (myCursor < myTemplate.length()) {
        nextChar = myTemplate.charAt(myCursor);
        if (isLatinLetter(nextChar) || isDigit(nextChar)) {
          gsb.append(nextChar);
          myCursor++;
        } else {
          break;
        }
      }
      if (gsb.length() == 0) throw new IllegalArgumentException("named capturing group has 0 length name");
      if (nextChar != '}') throw new IllegalArgumentException("named capturing group is missing trailing '}'");
      String gname = gsb.toString();
      if (isDigit(gname.charAt(0))) {
        throw new IllegalArgumentException("capturing group name {" + gname + "} starts with digit character");
      }
      myCursor++;
      group = myMatcher.group(gname);
    } else {
      // The first number is always a group
      int refNum = (int)nextChar - '0';
      if (refNum < 0 || refNum > 9) throw new IllegalArgumentException("Illegal group reference");
      // Capture the largest legal group string
      while (true) {
        if (myCursor >= myTemplate.length()) break;
        int nextDigit = myTemplate.charAt(myCursor) - '0';
        if (nextDigit < 0 || nextDigit > 9) break;
        int newRefNum = (refNum * 10) + nextDigit;
        if (myMatcher.groupCount() < newRefNum) break;
        refNum = newRefNum;
        myCursor++;
      }
      group = myMatcher.group(refNum);
    }
    if (group != null) {
      myReplacement.append(group);
    }
  }

  private String generateResult() {
    StringBuilder result;
    if (myConversionRegions.isEmpty()) {
      result = myReplacement;
    }
    else {
      CaseConversionRegion lastRegion = myConversionRegions.get(myConversionRegions.size() - 1);
      if (lastRegion.end < 0 || lastRegion.end > myReplacement.length()) {
        lastRegion.end = myReplacement.length();
      }
      result = new StringBuilder();
      int currentOffset = 0;
      for (CaseConversionRegion conversionRegion : myConversionRegions) {
        result.append(myReplacement, currentOffset, conversionRegion.start);
        String region = myReplacement.substring(conversionRegion.start, conversionRegion.end);
        result.append(conversionRegion.toUpperCase ? region.toUpperCase(Locale.getDefault()) : region.toLowerCase(Locale.getDefault()));
        currentOffset = conversionRegion.end;
      }
      result.append(myReplacement, currentOffset, myReplacement.length());
    }
    return result.toString();
  }

  private void startConversionForCharacter(boolean toUpperCase) {
    int currentOffset = myReplacement.length();
    CaseConversionRegion lastRegion = myConversionRegions.isEmpty() ? null : myConversionRegions.get(myConversionRegions.size() - 1);
    if (lastRegion == null || lastRegion.end >= 0 && lastRegion.end <= currentOffset) {
      myConversionRegions.add(new CaseConversionRegion(currentOffset, currentOffset + 1, toUpperCase));
    }
  }

  private void startConversionForRegion(boolean toUpperCase) {
    int currentOffset = myReplacement.length();
    CaseConversionRegion lastRegion = myConversionRegions.isEmpty() ? null : myConversionRegions.get(myConversionRegions.size() - 1);
    if (lastRegion == null) {
      myConversionRegions.add(new CaseConversionRegion(currentOffset, -1, toUpperCase));
    }
    else if (lastRegion.start == currentOffset) {
      lastRegion.end = -1;
      lastRegion.toUpperCase = toUpperCase;
    }
    else {
      if (lastRegion.end == -1) {
        if (lastRegion.toUpperCase == toUpperCase) {
          return;
        }
        lastRegion.end = currentOffset;
      }
      myConversionRegions.add(new CaseConversionRegion(currentOffset, -1, toUpperCase));
    }
  }

  private void resetConversionState() {
    if (!myConversionRegions.isEmpty()) {
      int currentOffset = myReplacement.length();
      int lastIndex = myConversionRegions.size() - 1;
      CaseConversionRegion lastRegion = myConversionRegions.get(lastIndex);
      if (lastRegion.start >= currentOffset) {
        myConversionRegions.remove(lastIndex);
      }
      else if (lastRegion.end == -1) {
        lastRegion.end = currentOffset;
      }
    }
  }

  private static boolean isLatinLetter(int ch) {
    return ((ch-'a')|('z'-ch)) >= 0 || ((ch-'A')|('Z'-ch)) >= 0;
  }

  private static boolean isDigit(int ch) {
    return ((ch-'0')|('9'-ch)) >= 0;
  }

  private static final class CaseConversionRegion {
    private final int start;
    private int end;
    private boolean toUpperCase;

    private CaseConversionRegion(int start, int end, boolean toUpperCase) {
      this.start = start;
      this.end = end;
      this.toUpperCase = toUpperCase;
    }
  }

  interface MatchGroupContainer {
    String group(String name);
    String group(int num);
    int groupCount();
  }
}
