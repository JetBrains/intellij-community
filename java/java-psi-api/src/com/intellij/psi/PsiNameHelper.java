/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Service for validating and parsing Java identifiers.
 *
 * @see com.intellij.psi.JavaPsiFacade#getNameHelper()
 */
public abstract class PsiNameHelper {
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
   * @param languageLevel to check text against. For instance 'assert' or 'enum' might or might not be identifiers depending on language level
   * @return true if the text is an identifier, false otherwise
   */
  public abstract boolean isIdentifier(@Nullable String text, LanguageLevel languageLevel);

  /**
   * Checks if the specified text is a Java keyword, using the language level of the project
   * with which the name helper is associated.
   *
   * @param text the text to check.
   * @return true if the text is a keyword, false otherwise
   */
  public abstract boolean isKeyword(@Nullable String text);

  /**
   * Checks if the specified string is a qualified name (sequence of identifiers separated by
   * periods).
   *
   * @param text the text to check.
   * @return true if the text is a qualified name, false otherwise.
   */
  public abstract boolean isQualifiedName(@Nullable String text);

  @NotNull
  public static String getShortClassName(@NotNull String referenceText) {
    return getShortClassName(referenceText, true);
  }

  private static String getShortClassName(String referenceText, boolean removeWhitespace) {
    int lessPos = referenceText.length();
    int bracesBalance = 0;
    int i;
    loop:
    for (i = referenceText.length() - 1; i >= 0; i--) {
      final char aChar = referenceText.charAt(i);
      switch (aChar) {
        case ')':
        case '>':
          bracesBalance++;
          break;

        case '(':
        case '<':
          bracesBalance--;
          lessPos = i;
          break;

        case '@':
        case '.':
          if (bracesBalance == 0) break loop;
          break;

        default:
          if (bracesBalance == 0 && removeWhitespace && !Character.isWhitespace(aChar) && !Character.isJavaIdentifierPart(aChar)) {
            return getShortClassName(removeWhitespace(referenceText), false);
          }
      }
    }
    String sub = referenceText.substring(i + 1, lessPos).trim();
    return sub.length() == referenceText.length() ? sub : new String(sub);
  }

  public static String getPresentableText(PsiJavaCodeReferenceElement ref) {
    final String referenceName = ref.getReferenceName();

    PsiType[] typeParameters = ref.getTypeParameters();
    return getPresentableText(referenceName, typeParameters);
  }

  public static String getPresentableText(final String referenceName, final PsiType[] typeParameters) {
    if (typeParameters.length > 0) {
      StringBuilder buffer = new StringBuilder();
      buffer.append(referenceName);
      buffer.append("<");
      for (int i = 0; i < typeParameters.length; i++) {
        buffer.append(typeParameters[i].getPresentableText());
        if (i < typeParameters.length - 1) buffer.append(", ");
      }
      buffer.append(">");
      return buffer.toString();
    }

    return referenceName != null ? referenceName : "";
  }

  public static String getQualifiedClassName(String referenceText, boolean removeWhitespace) {
    if (removeWhitespace) {
      referenceText = removeWhitespace(referenceText);
    }
    if (referenceText.indexOf('<') < 0) return referenceText;
    final StringBuilder buffer = new StringBuilder(referenceText.length());
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

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(?:\\s)|(?:/\\*.*\\*/)|(?://[^\\n]*)");
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
    for (final char aChar : chars) {
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

}
