/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for validating and parsing Java identifiers.
 *
 * @see com.intellij.psi.PsiManager#getNameHelper()
 */
public abstract class PsiNameHelper {
  @NonNls private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(?:\\s)|(?:/\\*.*\\*/)|(?://[^\\n]*)");

  /**
   * Checks if the specified text is a Java identifier, using the language level of the project
   * with which the name helper is associated to filter out keywords.
   *
   * @param text the text to check.
   * @return true if the text is an identifier, false otherwise
   */
  public abstract boolean isIdentifier(@Nullable String text);

  /**
   * Checks if the specified text is a Java identifier, using the specified language level
   * with which the name helper is associated to filter out keywords.
   *
   * @param text the text to check.
   * @return true if the text is an identifier, false otherwise
   */
  public abstract boolean isIdentifier(String text, LanguageLevel languageLevel);

  /**
   * Checks if the specified text is a Java keyword, using the language level of the project
   * with which the name helper is associated.
   *
   * @param text the text to check.
   * @return true if the text is a keyword, false otherwise
   */
  public abstract boolean isKeyword(String text);

  /**
   * Checks if the specified string is a qualified name (sequence of identifiers separated by
   * periods).
   *
   * @param text the text to check.
   * @return true if the text is a qualified name, false otherwise.
   */
  public abstract boolean isQualifiedName(String text);

  public static String getShortClassName(@NotNull String referenceText) {
    return getShortClassName(referenceText, true);
  }

  private static String getShortClassName(String referenceText, boolean flag) {
    final char[] chars = referenceText.toCharArray();
    int lessPos = chars.length;
    int count = 0;
    for (int i = chars.length - 1; i >= 0; i--) {
      final char aChar = chars[i];
      switch (aChar) {
        case ')':
        case '>':
          count++;
          break;

        case '(':
        case '<':
          count--;
          lessPos = i;
          break;

        case '@':
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

  public static String getPresentableText(PsiJavaCodeReferenceElement ref) {
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
   * They go in left-to-right order: <code>A&lt;List&lt;String&gt&gt;.B&lt;Integer&gt;</code> yields
   * <code>["List&lt;String&gt","Integer"]</code>
   *
   * @param referenceText the text of the reference to calculate type parameters for.
   * @return the calculated array of type parameters. 
   */
  public static String[] getClassParametersText(String referenceText) {
    if (referenceText.indexOf('<') < 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    referenceText = removeWhitespace(referenceText);
    final char[] chars = referenceText.toCharArray();
    int count = 0;
    int dim = 0;
    for(final char aChar : chars) {
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
   * Splits an identifier into words, separated with underscores or upper-case characters
   * (camel-case).
   *
   * @param name the identifier to split.
   * @return the array of strings into which the identifier has been split.
   */
  public static String[] splitNameIntoWords(@NotNull String name) {
    final String[] underlineDelimited = name.split("_");
    List<String> result = new ArrayList<String>();
    for (String word : underlineDelimited) {
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
