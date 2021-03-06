// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

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

  public static @NotNull @NlsSafe String getShortClassName(@NotNull String referenceText) {
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

  /**
   * @param ref reference to get presentable text for
   * @return presentable text that contains a reference name and presentable text of type parameters, if any.
   * No annotations are displayed
   */
  public static @NotNull String getPresentableText(@NotNull PsiJavaCodeReferenceElement ref) {
    String name = ref.getReferenceName();
    PsiType[] types = ref.getTypeParameters();
    if (types.length == 0) {
      return name != null ? name : "";
    }

    StringBuilder buffer = new StringBuilder();
    buffer.append(name);
    appendTypeArgs(buffer, types, false, false);
    return buffer.toString();
  }

  /**
   * @param refName name of a reference to get presentable text for
   * @param annotations reference annotations
   * @param types reference type parameters
   * @return presentable text that contains supplied annotations, a reference name and presentable text of type parameters, if any,
   * including their annotations.
   */
  public static @NotNull String getPresentableText(@Nullable String refName, PsiAnnotation @NotNull [] annotations, PsiType @NotNull [] types) {
    if (types.length == 0 && annotations.length == 0) {
      return refName != null ? refName : "";
    }

    StringBuilder buffer = new StringBuilder();
    appendAnnotations(buffer, annotations, false);
    buffer.append(refName);
    appendTypeArgs(buffer, types, false, true);
    return buffer.toString();
  }

  /**
   * @param referenceText text of the inner class reference (without annotations), e.g. {@code A.B<C>.D<E, F.G>}
   * @return outer class reference (e.g. {@code A.B<C>}); empty string if the original reference is unqualified  
   */
  @Contract(pure = true)
  public static @NotNull String getOuterClassReference(String referenceText) {
    int stack = 0;
    for (int i = referenceText.length() - 1; i >= 0; i--) {
      char c = referenceText.charAt(i);
      switch (c) {
        case '<':
          stack--;
          break;
        case '>':
          stack++;
          break;
        case '.':
          if (stack == 0) return referenceText.substring(0, i);
      }
    }

    return "";
  }

  /**
   * @param referenceText text of the class reference (without annotations), e.g. {@code A.B<C>.D<E, F.G>}
   * @return qualified class name (e.g. {@code A.B.D}).  
   */
  @Contract(pure = true)
  public static @NotNull String getQualifiedClassName(@NotNull String referenceText, boolean removeWhitespace) {
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
  private static @NotNull String removeWhitespace(@NotNull String referenceText) {
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
   * They go in left-to-right order: {@code A<List<String>, B<Integer>>} yields
   * {@code ["List<String>", "B<Integer>"]}. Parameters of the outer reference are ignored:
   * {@code A<List<String>>.B<Integer>} yields {@code ["Integer"]}
   *
   * @param referenceText the text of the reference to calculate type parameters for.
   * @return the calculated array of type parameters.
   */
  public static String @NotNull [] getClassParametersText(@NotNull String referenceText) {
    if (referenceText.indexOf('<') < 0) return ArrayUtilRt.EMPTY_STRING_ARRAY;
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

    if (level != 0) return ArrayUtilRt.EMPTY_STRING_ARRAY;

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
    if (level != 0 || dim == 0) return ArrayUtilRt.EMPTY_STRING_ARRAY;

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

  public static void appendTypeArgs(@NotNull StringBuilder sb, PsiType @NotNull [] types, boolean canonical, boolean annotated) {
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
        sb.append(type.getPresentableText(annotated));
      }
    }
    sb.append('>');
  }

  public static boolean appendAnnotations(@NotNull StringBuilder sb, PsiAnnotation @NotNull [] annotations, boolean canonical) {
    return appendAnnotations(sb, Arrays.asList(annotations), canonical);
  }

  public static boolean appendAnnotations(@NotNull StringBuilder sb, @NotNull List<? extends PsiAnnotation> annotations, boolean canonical) {
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