/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers, Mark Scott
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
package com.siyeh.ig.portability;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.portability.mediatype.*;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class HardcodedFileSeparatorsInspection extends BaseInspection {

  private static final char BACKSLASH = '\\';
  private static final char SLASH = '/';

  private static class Holder {
  /**
   * The regular expression pattern that matches strings which are likely to
   * be date formats. <code>Pattern</font></b> instances are immutable, so
   * caching the pattern like this is still thread-safe.
   */
  @NonNls private static final Pattern DATE_FORMAT_PATTERN =
    Pattern.compile("\\b[dDmM]+/[dDmM]+(/[yY]+)?");
  /**
   * A regular expression that matches strings which represent example MIME
   * media types.
   */
  @NonNls private static final String EXAMPLE_MIME_MEDIA_TYPE_PATTERN =
    "example/\\p{Alnum}+(?:[.\\-\\\\+]\\p{Alnum}+)*";
  /**
   * A regular expression pattern that matches strings which start with a URL
   * protocol, as they're likely to actually be URLs.
   */
  @NonNls private static final Pattern URL_PATTERN =
    Pattern.compile("^[a-z][a-z0-9+\\-:]+://.*$");

  /**
   * All mimetypes, see <a href="http://www.iana.org/assignments/media-types/">IANA</a>
   */
  private static final Set<String> mimeTypes = new HashSet<>();

  static {
    for (ImageMediaType imageMediaType : ImageMediaType.values()) {
      mimeTypes.add(imageMediaType.toString());
    }
    for (ApplicationMediaType applicationMediaType :
      ApplicationMediaType.values()) {
      mimeTypes.add(applicationMediaType.toString());
    }
    for (AudioMediaType audioMediaType : AudioMediaType.values()) {
      mimeTypes.add(audioMediaType.toString());
    }
    for (MessageMediaType messageMediaType : MessageMediaType.values()) {
      mimeTypes.add(messageMediaType.toString());
    }
    for (ModelMediaType modelMediaType : ModelMediaType.values()) {
      mimeTypes.add(modelMediaType.toString());
    }
    for (MultipartMediaType multipartMediaType :
      MultipartMediaType.values()) {
      mimeTypes.add(multipartMediaType.toString());
    }
    for (TextMediaType textMediaType : TextMediaType.values()) {
      mimeTypes.add(textMediaType.toString());
    }
    for (VideoMediaType videoContentTypeMediaType :
      VideoMediaType.values()) {
      mimeTypes.add(videoContentTypeMediaType.toString());
    }
  }

  /**
   * All {@link TimeZone} IDs.
   */
  private static final Set<String> timeZoneIds = new HashSet<>();

  static {
    ContainerUtil.addAll(timeZoneIds, TimeZone.getAvailableIDs());
  }
  }

  private final MethodMatcher myMethodMatcher;

  /**
   * @noinspection PublicField
   */
  public boolean m_recognizeExampleMediaType = false;


  public HardcodedFileSeparatorsInspection() {
    myMethodMatcher = getMatcherByDefault();
  }

  private static MethodMatcher getMatcherByDefault() {
    return new MethodMatcher(false, "ignoredCalls")
      .add(CommonClassNames.JAVA_LANG_CLASS, "getResource|getResourceAsStream")
      .add("java.lang.ClassLoader", "findResource|getResource|getResources|resources|getSystemResource|getSystemResources|getSystemResourceAsStream")
      .finishDefault();
  }

    @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    myMethodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    super.writeSettings(element);
    myMethodMatcher.writeSettings(element);
  }

  @Override
  @NotNull
  public String getID() {
    return "HardcodedFileSeparator";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "hardcoded.file.separator.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_recognizeExampleMediaType", InspectionGadgetsBundle.message(
        "hardcoded.file.separator.include.option")),
      myMethodMatcher.getTable(InspectionGadgetsBundle.message(
        "hardcoded.file.separator.ignore.methods.option")).prefix("myMethodMatcher"));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HardcodedFileSeparatorsVisitor();
  }

  private class HardcodedFileSeparatorsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);

      final PsiType type = expression.getType();
      if (TypeUtils.isJavaLangString(type)) {
        final String value = (String)expression.getValue();
        if (!isHardcodedFilenameString(value)) {
          return;
        }
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
        if (parent != null) {
          final PsiElement grandParent = parent.getParent();
          if (grandParent instanceof PsiMethodCallExpression methodCallExpression) {
            if (MethodCallUtils.isCallToRegexMethod(methodCallExpression) || myMethodMatcher.matches(methodCallExpression)) {
              return;
            }
          }
          else if (grandParent instanceof PsiNewExpression newExpression) {
            if (TypeUtils.expressionHasTypeOrSubtype(newExpression, "javax.swing.ImageIcon")) {
              return;
            }
          }
        }
        registerErrorInString(expression);
      }
      else if (type != null && type.equals(PsiTypes.charType())) {
        final Character value = (Character)expression.getValue();
        if (value == null) {
          return;
        }
        final char unboxedValue = value.charValue();
        if (unboxedValue == BACKSLASH || unboxedValue == SLASH) {
          registerErrorAtOffset(expression, 1, expression.getTextLength() - 2);
        }
      }
    }

    /**
     * Check whether a string is likely to be a filename containing one or more
     * hard-coded file separator characters. The method does some simple
     * analysis of the string to determine whether it's likely to be some other
     * type of data - a URL, a date format, or an XML fragment - before deciding
     * that the string is a filename.
     *
     * @param string The string to examine.
     * @return {@code true} if the string is likely to be a filename
     *         with hardcoded file separators, {@code false}
     *         otherwise.
     */
    private boolean isHardcodedFilenameString(String string) {
      if (string == null) {
        return false;
      }
      if (string.indexOf(SLASH) == -1 &&
          string.indexOf(BACKSLASH) == -1) {
        return false;
      }
      for (int i = 0, length = string.length(); i < length; i++) {
        char c = string.charAt(i);
        if (c == '\b' || c == '\n' || c == '\t' || c == '\r' || c == '\f') {
          return false;
        }
      }
      if (OSAgnosticPathUtil.startsWithWindowsDrive(string)) {
        return true;
      }
      if (isXMLString(string)) {
        return false;
      }
      if (isDateFormatString(string)) {
        return false;
      }
      if (isURLString(string)) {
        return false;
      }
      if (isMediaTypeString(string)) {
        return false;
      }
      return !isTimeZoneIdString(string);
    }

    /**
     * Highlights the backward or forward slashes in a string literal.
     */
    private void registerErrorInString(@NotNull PsiLiteralExpression expression) {
      final String text = expression.getText();
      final int[] offsets = new int[text.length() + 1];
      final CharSequence result = CodeInsightUtilCore.parseStringCharacters(text, offsets);
      if (result == null) {
        return;
      }
      for (int i = 0, length = result.length(); i < length; i++) {
        final char c = result.charAt(i);
        if (c == SLASH || c == BACKSLASH) {
          registerErrorAtOffset(expression, offsets[i], offsets[i + 1] - offsets[i]);
        }
      }
    }

    /**
     * Check whether a string containing at least one '/' or '\' character is
     * likely to be a fragment of XML.
     *
     * @param string The string to examine.
     * @return {@code true} if the string is likely to be an XML
     *         fragment, or {@code false} if not.
     */
    private static boolean isXMLString(String string) {
      return string.contains("</") || string.contains("/>");
    }

    /**
     * Check whether a string containing at least one '/' or '\' character is
     * likely to be a date format string.
     *
     * @param string The string to check.
     * @return {@code true} if the string is likely to be a date
     *         string, {@code false} if not.
     */
    private static boolean isDateFormatString(String string) {
      if (string.length() < 3) {
        // A string this short is very unlikely to be a date format.
        return false;
      }
      final int strLength = string.length();
      final char startChar = string.charAt(0);
      final char endChar = string.charAt(strLength - 1);
      if (startChar == SLASH || endChar == SLASH) {
        // Most likely it's a filename if the string starts or ends
        // with a slash.
        return false;
      }
      else if (OSAgnosticPathUtil.startsWithWindowsDrive(string)) {
        // Most likely this is a Windows-style full file name.
        return false;
      }
      final Matcher dateFormatMatcher = Holder.DATE_FORMAT_PATTERN.matcher(string);
      return dateFormatMatcher.find();
    }

    /**
     * Checks whether a string containing at least one '/' or '\' character is
     * likely to be a URL.
     *
     * @param string The string to check.
     * @return {@code true} if the string is likely to be a URL,
     *         {@code false} if not.
     */
    private static boolean isURLString(String string) {
      final Matcher urlMatcher = Holder.URL_PATTERN.matcher(string);
      return urlMatcher.find();
    }

    /**
     * Checks whether a string containing at least one '/' character is
     * likely to be a MIME media type.  See the
     * <a href="http://www.iana.org/assignments/media-types/">IANA</a>
     * documents for registered MIME media types.
     *
     * @param string The string to check.
     * @return {@code true} if the string is likely to be a MIME
     *         media type, {@code false} if not.
     */
    private boolean isMediaTypeString(String string) {
      // IANA doesn't specify a pattern for the subtype of example content
      // types but other subtypes seem to be one or more groups of
      // alphanumerics characters, the groups being separated by a single
      // period (.), hyphen (-) or plus (+) character
      //
      // Valid examples:
      //
      // "example/foo"
      // "example/foo.bar"
      // "example/foo-bar+baz"
      // "example/foo1.2006-bar"
      //
      // Invalid examples:
      //
      // "example/foo$bar"              ($ isn't a valid separator)
      // "example/foo."                 (can't end with a separator)
      //
      if (m_recognizeExampleMediaType &&
          string.matches(Holder.EXAMPLE_MIME_MEDIA_TYPE_PATTERN)) {
        return true;
      }
      return Holder.mimeTypes.contains(string);
    }

    /**
     * Checks whether a string containing at least one '/' character is
     * likely to be a {@link TimeZone} ID.
     *
     * @param string The string to check.
     * @return {@code true} if the string is likely to be a
     *         TimeZone ID, {@code false} if not.
     */
    private static boolean isTimeZoneIdString(String string) {
      return Holder.timeZoneIds.contains(string);
    }
  }
}
