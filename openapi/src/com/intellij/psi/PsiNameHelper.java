/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.ArrayUtil;
import com.intellij.pom.java.LanguageLevel;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class PsiNameHelper {
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(?:\\s)|(?:/\\*.*\\*/)|(?://[^\\n]*)");

  public abstract boolean isIdentifier(String text);

  public abstract boolean isIdentifier(String text, LanguageLevel languageLevel);

  public abstract boolean isKeyword(String text);

  public abstract boolean isQualifiedName(String text);

  public static String getShortClassName(String referenceText) {
    return getShortClassName(referenceText, true);
  }

  private static String getShortClassName(String referenceText, boolean flag) {
    final char[] chars = referenceText.toCharArray();
    int lessPos = chars.length;
    int count = 0;
    for (int i = chars.length - 1; i >= 0; i--) {
      final char aChar = chars[i];
      switch (aChar) {
        case '>':
          count++;
          break;
        case '<':
          count--;
          lessPos = i;
          break;
        case '.':
          if (count == 0) return new String(chars, i + 1, lessPos - (i + 1)).trim();
          break;
        default:
          if (count == 0) {
            if (Character.isWhitespace(aChar)) {
              break;
            }
            else if (flag && !Character.isJavaIdentifierPart(aChar)) {
              return getShortClassName(
                removeWhitespace(referenceText), false);
            }
          }
      }
    }
    return new String(chars, 0, lessPos).trim();
  }

  public static final String getPresentableText(PsiJavaCodeReferenceElement ref) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(ref.getReferenceName());
    PsiType[] typeParameters = ref.getTypeParameters();
    if (typeParameters.length > 0) {
      buffer.append("<");
      for (int i = 0; i < typeParameters.length; i++) {
        buffer.append(typeParameters[i].getPresentableText());
        if (i < typeParameters.length - 1) buffer.append(", ");
      }
      buffer.append(">");
    }

    return buffer.toString();
  }

  public static String getQualifiedClassName(String referenceText, boolean removeWhitespace) {
    if (removeWhitespace) {
      referenceText = removeWhitespace(referenceText);
    }
    if (referenceText.indexOf('<') < 0) return referenceText;
    final StringBuffer buffer = new StringBuffer(referenceText.length());
    final char[] chars = referenceText.toCharArray();
    int gtPos = 0;
    int count = 0;
    for (int i = 0; i < chars.length; i++) {
      final char aChar = chars[i];
      switch (aChar) {
        case '<':
          count++;
          if (count == 1) buffer.append(new String(chars, gtPos, i - gtPos));
          break;
        case '>':
          count--;
          gtPos = i + 1;
          break;
      }
    }
    if (count == 0) {
      buffer.append(new String(chars, gtPos, chars.length - gtPos));
    }
    return buffer.toString();
  }

  private static String removeWhitespace(String referenceText) {
    return WHITESPACE_PATTERN.matcher(referenceText).replaceAll("");
  }

  /**
   * Obtains text of all type parameter values in a reference.
   * They go in lft-to-right order: <code>A&lt;List&lt;String&gt&gt;.B&lt;Integer&gt;</code> yields
   * <code>["List&lt;String&gt","Integer"]</code>
   *
   * @param referenceText
   * @return
   */
  public static String[] getClassParametersText(String referenceText) {
    if (referenceText.indexOf('<') < 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    referenceText = removeWhitespace(referenceText);
    final char[] chars = referenceText.toCharArray();
    int count = 0;
    int dim = 0;
    for (int i = 0; i < chars.length; i++) {
      final char aChar = chars[i];
      switch (aChar) {
        case '<':
          count++;
          if (count == 1) dim++;
          break;
        case ',':
          if (count == 1) dim++;
          break;
        case '>':
          count--;
          break;
      }
    }
    if (count != 0 || dim == 0) return ArrayUtil.EMPTY_STRING_ARRAY;

    final String[] result = new String[dim];
    dim = 0;
    int ltPos = 0;
    for (int i = 0; i < chars.length; i++) {
      final char aChar = chars[i];
      switch (aChar) {
        case '<':
          count++;
          if (count == 1) ltPos = i;
          break;
        case ',':
          if (count == 1) {
            result[dim++] = new String(chars, ltPos + 1, i - ltPos - 1);
            ltPos = i;
          }
          break;
        case '>':
          count--;
          if (count == 0) result[dim++] = new String(chars, ltPos + 1, i - ltPos - 1);
          break;
      }
    }

    return result;
  }

  /**
   * Splits identifier into words.
   * @param name
   * @return
   */
  public static String[] splitNameIntoWords(String name) {
    final String[] underlineDelimited = name.split("_");
    List<String> result = new ArrayList<String>();
    for (int i = 0; i < underlineDelimited.length; i++) {
      String word = underlineDelimited[i];
      addAllWords(word, result);
    }
    return result.toArray(new String[result.size()]);
  }

  private static final int NO_WORD = 0;
  private static final int PREV_UC = 1;
  private static final int WORD = 2;

  private static void addAllWords(String word, List<String> result) {
    CharacterIterator it = new StringCharacterIterator(word);
    StringBuffer b = new StringBuffer();
    int state = NO_WORD;
    char curPrevUC = '\0';
    for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
      switch (state) {
        case NO_WORD:
          if (!Character.isUpperCase(c)) {
            b.append(c);
            state = WORD;
          }
          else {
            state = PREV_UC;
            curPrevUC = c;
          }
          break;
        case PREV_UC:
          if (!Character.isUpperCase(c)) {
            b = startNewWord(result, b, curPrevUC);
            b.append(c);
            state = WORD;
          }
          else {
            b.append(curPrevUC);
            state = PREV_UC;
            curPrevUC = c;
          }
          break;
        case WORD:
          if (Character.isUpperCase(c)) {
            startNewWord(result, b, c);
            b.setLength(0);
            state = PREV_UC;
            curPrevUC = c;
          }
          else {
            b.append(c);
          }
          break;
      }
    }
    if (state == PREV_UC) {
      b.append(curPrevUC);
    }
    result.add(b.toString());
  }

  private static StringBuffer startNewWord(List<String> result, StringBuffer b, char c) {
    if (b.length() > 0) {
      result.add(b.toString());
    }
    b = new StringBuffer();
    b.append(c);
    return b;
  }
}