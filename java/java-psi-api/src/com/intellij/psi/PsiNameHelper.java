/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * Service for validating and parsing Java identifiers.
 */
public abstract class PsiNameHelper {

  public static PsiNameHelper getInstance(Project project) {
    return ServiceManager.getService(project, PsiNameHelper.class);
  }

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
  public abstract boolean isIdentifier(@Nullable String text, @NotNull LanguageLevel languageLevel);

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
    int lessPos = referenceText.length();
    int bracesBalance = 0;
    int i;

    loop:
    for (i = referenceText.length() - 1; i >= 0; i--) {
      char ch = referenceText.charAt(i);
      switch (ch) {
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
          if (bracesBalance <= 0) break loop;
          break;

        default:
          if (Character.isWhitespace(ch) && bracesBalance <= 0) {
            for (int j = i + 1; j < lessPos; j++) {
              if (!Character.isWhitespace(referenceText.charAt(j))) break loop;
            }
            lessPos = i;
          }
      }
    }

    return referenceText.substring(i + 1, lessPos).trim();
  }

  @NotNull
  public static String getPresentableText(@NotNull PsiJavaCodeReferenceElement ref) {
    String name = ref.getReferenceName();
    PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class);
    return getPresentableText(name, notNull(annotations, PsiAnnotation.EMPTY_ARRAY), ref.getTypeParameters());
  }

  @NotNull
  public static String getPresentableText(@Nullable String refName, @NotNull PsiAnnotation[] annotations, @NotNull PsiType[] types) {
    if (types.length == 0 && annotations.length == 0) {
      return refName != null ? refName : "";
    }

    StringBuilder buffer = new StringBuilder();
    appendAnnotations(buffer, annotations, false);
    buffer.append(refName);
    appendTypeArgs(buffer, types, false, true);
    return buffer.toString();
  }

  @NotNull
  public static String getQualifiedClassName(@NotNull String referenceText, boolean removeWhitespace) {
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
  private static String removeWhitespace(@NotNull String referenceText) {
    boolean needsChange = false;
    for (int i = 0; i < referenceText.length(); i++) {
      char c = referenceText.charAt(i);
      if (c == '/' || Character.isWhitespace(c)) {
        needsChange = true;
        break;
      }
    }
    if (!needsChange) return referenceText;

    return WHITESPACE_PATTERN.matcher(referenceText).replaceAll("");
  }

  /**
   * Obtains text of all type parameter values in a reference.
   * They go in left-to-right order: {@code A<List<String&gt, B<Integer>>} yields
   * {@code ["List<String&gt", "B<Integer>"]}. Parameters of the outer reference are ignored:
   * {@code A<List<String&gt>.B<Integer>} yields {@code ["Integer"]}
   *
   * @param referenceText the text of the reference to calculate type parameters for.
   * @return the calculated array of type parameters.
   */
  @NotNull
  public static String[] getClassParametersText(@NotNull String referenceText) {
    if (referenceText.indexOf('<') < 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    final char[] chars = referenceText.toCharArray();
    int afterLastDotIndex = 0;

    int level = 0;
    for (int i = 0; i < chars.length; i++) {
      char aChar = chars[i];
      switch (aChar) {
        case '<':
          level++;
          break;
        case '.':
          if (level == 0) afterLastDotIndex = i + 1;
          break;
        case '>':
          level--;
          break;
      }
    }

    if (level != 0) return ArrayUtil.EMPTY_STRING_ARRAY;

    int dim = 0;
    for (int i = afterLastDotIndex; i < chars.length; i++) {
      char aChar = chars[i];
      switch (aChar) {
        case '<':
          level++;
          if (level == 1) dim++;
          break;
        case ',':
          if (level == 1) dim++;
          break;
        case '>':
          level--;
          break;
      }
    }
    if (level != 0 || dim == 0) return ArrayUtil.EMPTY_STRING_ARRAY;

    final String[] result = new String[dim];
    dim = 0;
    int ltPos = 0;
    for (int i = afterLastDotIndex; i < chars.length; i++) {
      final char aChar = chars[i];
      switch (aChar) {
        case '<':
          level++;
          if (level == 1) ltPos = i;
          break;
        case ',':
          if (level == 1) {
            result[dim++] = new String(chars, ltPos + 1, i - ltPos - 1);
            ltPos = i;
          }
          break;
        case '>':
          level--;
          if (level == 0) result[dim++] = new String(chars, ltPos + 1, i - ltPos - 1);
          break;
      }
    }

    return result;
  }

  public static boolean isSubpackageOf(@NotNull String subpackageName, @NotNull String packageName) {
    return subpackageName.equals(packageName) ||
           subpackageName.startsWith(packageName) && subpackageName.charAt(packageName.length()) == '.';
  }

  public static void appendTypeArgs(@NotNull StringBuilder sb, @NotNull PsiType[] types, boolean canonical, boolean annotated) {
    if (types.length == 0) return;

    sb.append('<');
    for (int i = 0; i < types.length; i++) {
      if (i > 0) {
        sb.append(canonical ? "," : ", ");
      }

      PsiType type = types[i];
      if (canonical) {
        sb.append(type.getCanonicalText(annotated));
      }
      else {
        sb.append(type.getPresentableText());
      }
    }
    sb.append('>');
  }

  public static boolean appendAnnotations(@NotNull StringBuilder sb, @NotNull PsiAnnotation[] annotations, boolean canonical) {
    return appendAnnotations(sb, Arrays.asList(annotations), canonical);
  }

  public static boolean appendAnnotations(@NotNull StringBuilder sb, @NotNull List<PsiAnnotation> annotations, boolean canonical) {
    boolean updated = false;
    for (PsiAnnotation annotation : annotations) {
      if (canonical) {
        String name = annotation.getQualifiedName();
        if (name != null) {
          sb.append('@').append(name).append(annotation.getParameterList().getText()).append(' ');
          updated = true;
        }
      }
      else {
        PsiJavaCodeReferenceElement refElement = annotation.getNameReferenceElement();
        if (refElement != null) {
          sb.append('@').append(refElement.getText()).append(' ');
          updated = true;
        }
      }
    }
    return updated;
  }

  public static boolean isValidModuleName(@NotNull String name, @NotNull PsiElement context) {
    PsiNameHelper helper = getInstance(context.getProject());
    LanguageLevel level = PsiUtil.getLanguageLevel(context);
    return StringUtil.split(name, ".", true, false).stream().allMatch(part -> helper.isIdentifier(part, level));
  }
}