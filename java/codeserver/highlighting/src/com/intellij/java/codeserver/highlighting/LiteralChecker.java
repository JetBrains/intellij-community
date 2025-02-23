// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LiteralChecker {
  private static final Pattern FP_LITERAL_PARTS =
    Pattern.compile("(?:" +
                    "0x([_\\p{XDigit}]*)\\.?([_\\p{XDigit}]*)p[+-]?([_\\d]*)" +
                    "|" +
                    "([_\\d]*)\\.?([_\\d]*)e?[+-]?([_\\d]*)" +
                    ")[fd]?");
  private final @NotNull JavaErrorVisitor myVisitor;

  LiteralChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void getLiteralExpressionParsingError(@NotNull PsiLiteralExpression expression) {
    PsiElement literal = expression.getFirstChild();
    assert literal instanceof PsiJavaToken : literal;
    IElementType type = ((PsiJavaToken)literal).getTokenType();
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD || type == JavaTokenType.NULL_KEYWORD) return;

    boolean isInt = ElementType.INTEGER_LITERALS.contains(type);
    boolean isFP = ElementType.REAL_LITERALS.contains(type);
    String rawText = isInt || isFP ? StringUtil.toLowerCase(literal.getText()) : literal.getText();
    String text = parseUnicodeEscapes(rawText, null);
    Object value = expression.getValue();

    if (isFP && text.startsWith(PsiLiteralUtil.HEX_PREFIX)) {
      myVisitor.checkFeature(expression, JavaFeature.HEX_FP_LITERALS);
    }
    else if (isInt && text.startsWith(PsiLiteralUtil.BIN_PREFIX)) {
      myVisitor.checkFeature(expression, JavaFeature.BIN_LITERALS);
    }
    if ((isInt || isFP) && text.contains("_")) {
      myVisitor.checkFeature(expression, JavaFeature.UNDERSCORES);
      if (!myVisitor.hasErrorResults()) checkUnderscores(expression, text, isInt);
    }
    if (myVisitor.hasErrorResults()) return;

    PsiElement parent = expression.getParent();
    if (type == JavaTokenType.INTEGER_LITERAL) {
      String cleanText = StringUtil.replace(text, "_", "");
      //literal 2147483648 may appear only as the operand of the unary negation operator -.
      if (!(cleanText.equals(PsiLiteralUtil._2_IN_31) &&
            parent instanceof PsiPrefixExpression prefixExpression &&
            prefixExpression.getOperationTokenType() == JavaTokenType.MINUS)) {
        if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
          myVisitor.report(JavaErrorKinds.LITERAL_HEXADECIMAL_NO_DIGITS.create(expression));
        }
        else if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
          myVisitor.report(JavaErrorKinds.LITERAL_BINARY_NO_DIGITS.create(expression));
        }
        else if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_31)) {
          myVisitor.report(JavaErrorKinds.LITERAL_INTEGER_TOO_LARGE.create(expression));
        }
      }
    }
    else if (type == JavaTokenType.LONG_LITERAL) {
      String cleanText = StringUtil.replace(StringUtil.trimEnd(text, 'l'), "_", "");
      //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
      if (!(cleanText.equals(PsiLiteralUtil._2_IN_63) &&
            parent instanceof PsiPrefixExpression prefixExpression &&
            prefixExpression.getOperationTokenType() == JavaTokenType.MINUS)) {
        if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
          myVisitor.report(JavaErrorKinds.LITERAL_HEXADECIMAL_NO_DIGITS.create(expression));
        }
        else if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
          myVisitor.report(JavaErrorKinds.LITERAL_BINARY_NO_DIGITS.create(expression));
        }
        else if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_63)) {
          myVisitor.report(JavaErrorKinds.LITERAL_LONG_TOO_LARGE.create(expression));
        }
      }
    }
    else if (isFP) {
      if (value == null) {
        myVisitor.report(JavaErrorKinds.LITERAL_FLOATING_MALFORMED.create(expression));
      }
      else if (value instanceof Float number) {
        if (number.isInfinite()) {
          myVisitor.report(JavaErrorKinds.LITERAL_FLOATING_TOO_LARGE.create(expression));
        }
        else if (number.floatValue() == 0 && !TypeConversionUtil.isFPZero(text)) {
          myVisitor.report(JavaErrorKinds.LITERAL_FLOATING_TOO_SMALL.create(expression));
        }
      }
      else if (value instanceof Double number) {
        if (number.isInfinite()) {
          myVisitor.report(JavaErrorKinds.LITERAL_FLOATING_TOO_LARGE.create(expression));
        }
        else if (number.doubleValue() == 0 && !TypeConversionUtil.isFPZero(text)) {
          myVisitor.report(JavaErrorKinds.LITERAL_FLOATING_TOO_SMALL.create(expression));
        }
      }
    }
    else if (type == JavaTokenType.CHARACTER_LITERAL) {
      if (!StringUtil.startsWithChar(text, '\'')) return;
      if (!StringUtil.endsWithChar(text, '\'') || text.length() == 1) {
        myVisitor.report(JavaErrorKinds.LITERAL_CHARACTER_UNCLOSED.create(expression));
        return;
      }
      int rawLength = rawText.length();
      StringBuilder chars = new StringBuilder(rawLength);
      int[] offsets = new int[rawLength + 1];
      final boolean success = CodeInsightUtilCore.parseStringCharacters(rawText, chars, offsets, false);
      if (!success) {
        myVisitor.report(JavaErrorKinds.LITERAL_CHARACTER_ILLEGAL_ESCAPE.create(
          expression, calculateErrorRange(rawText, offsets[chars.length()])));
      }
      int length = chars.length();
      if (length > 3) {
        myVisitor.report(JavaErrorKinds.LITERAL_CHARACTER_TOO_LONG.create(expression));
      }
      else if (length == 2) {
        myVisitor.report(JavaErrorKinds.LITERAL_CHARACTER_EMPTY.create(expression));
      }
      else {
        checkTextBlockEscapes(expression, rawText);
      }
    }
    else if (type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      if (type == JavaTokenType.STRING_LITERAL) {
        for (PsiElement element = expression.getFirstChild(); element != null; element = element.getNextSibling()) {
          if (element instanceof OuterLanguageElement) return;
        }

        if (!StringUtil.startsWithChar(text, '"')) return;
        if (!StringUtil.endsWithChar(text, '"') || text.length() == 1) {
          myVisitor.report(JavaErrorKinds.LITERAL_STRING_ILLEGAL_LINE_END.create(expression));
          return;
        }
        int length = rawText.length();
        StringBuilder chars = new StringBuilder(length);
        int[] offsets = new int[length + 1];
        boolean success = CodeInsightUtilCore.parseStringCharacters(rawText, chars, offsets, false);
        if (!success) {
          myVisitor.report(
            JavaErrorKinds.LITERAL_STRING_ILLEGAL_ESCAPE.create(expression, calculateErrorRange(rawText, offsets[chars.length()])));
        }
        else {
          checkTextBlockEscapes(expression, rawText);
        } 
      }
      else {
        if (!text.endsWith("\"\"\"")) {
          myVisitor.report(JavaErrorKinds.LITERAL_TEXT_BLOCK_UNCLOSED.create(expression));
        }
        else if (text.length() > 3) {
          checkTextBlockNewlineAfterOpeningQuotes(expression, text);
          if (myVisitor.hasErrorResults()) return;
          final int rawLength = rawText.length();
          StringBuilder chars = new StringBuilder(rawLength);
          int[] offsets = new int[rawLength + 1];
          boolean success = CodeInsightUtilCore.parseStringCharacters(rawText, chars, offsets, true);
          if (!success) {
            myVisitor.report(
              JavaErrorKinds.LITERAL_STRING_ILLEGAL_ESCAPE.create(expression, calculateErrorRange(rawText, offsets[chars.length()])));
          }
        }
      }
    }
  }

  private void checkTextBlockEscapes(@NotNull PsiLiteralExpression expression, @NotNull String text) {
    if (myVisitor.isApplicable(JavaFeature.TEXT_BLOCK_ESCAPES)) return;
    TextRange errorRange = PsiLiteralUtil.findSlashS(text);
    if (errorRange != null) {
      myVisitor.report(JavaErrorKinds.UNSUPPORTED_FEATURE.create(expression, JavaFeature.TEXT_BLOCK_ESCAPES));
    }
  }

  private void checkTextBlockNewlineAfterOpeningQuotes(@NotNull PsiLiteralValue expression, @NotNull String text) {
    int i = 3;
    char c = text.charAt(i);
    while (PsiLiteralUtil.isTextBlockWhiteSpace(c)) {
      i++;
      c = text.charAt(i);
    }
    if (c != '\n' && c != '\r') {
      myVisitor.report(JavaErrorKinds.LITERAL_TEXT_BLOCK_NO_NEW_LINE.create(expression));
    }
  }

  private void checkUnderscores(@NotNull PsiLiteralExpression expression, @NotNull String text, boolean isInt) {
    String[] parts;
    if (isInt) {
      int start = 0;
      if (text.startsWith(PsiLiteralUtil.HEX_PREFIX) || text.startsWith(PsiLiteralUtil.BIN_PREFIX)) start += 2;
      int end = text.length();
      if (StringUtil.endsWithChar(text, 'l')) --end;
      parts = new String[]{text.substring(start, end)};
    }
    else {
      Matcher matcher = FP_LITERAL_PARTS.matcher(text);
      if (matcher.matches()) {
        parts = new String[matcher.groupCount()];
        for (int i = 0; i < matcher.groupCount(); i++) {
          parts[i] = matcher.group(i + 1);
        }
      }
      else {
        parts = ArrayUtilRt.EMPTY_STRING_ARRAY;
      }
    }

    for (String part : parts) {
      if (part != null && (StringUtil.startsWithChar(part, '_') || StringUtil.endsWithChar(part, '_'))) {
        myVisitor.report(JavaErrorKinds.LITERAL_ILLEGAL_UNDERSCORE.create(expression));
        return;
      }
    }
  }

  void checkFragmentError(@NotNull PsiFragment fragment) {
    String text = InjectedLanguageManager.getInstance(myVisitor.project()).getUnescapedText(fragment);
    if (fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
      checkTextBlockNewlineAfterOpeningQuotes(fragment, text);
      if (myVisitor.hasErrorResults()) return;
    }
    int length = text.length();
    if (fragment.getTokenType() == JavaTokenType.STRING_TEMPLATE_END) {
      if (!StringUtil.endsWithChar(text, '\"') || length == 1) {
        myVisitor.report(JavaErrorKinds.LITERAL_STRING_ILLEGAL_LINE_END.create(fragment));
        return;
      }
    }
    if (text.endsWith("\\{")) {
      text = text.substring(0, length - 2);
      length -= 2;
    }
    StringBuilder chars = new StringBuilder(length);
    int[] offsets = new int[length + 1];
    boolean success = CodeInsightUtilCore.parseStringCharacters(text, chars, offsets, fragment.isTextBlock());
    if (!success) {
      myVisitor.report(JavaErrorKinds.LITERAL_STRING_ILLEGAL_ESCAPE.create(fragment, calculateErrorRange(text, offsets[chars.length()])));
    }
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  static @NotNull String parseUnicodeEscapes(@NotNull String text,
                                             @Nullable BiConsumer<? super Integer, ? super Integer> illegalEscapeConsumer) {
    // JLS 3.3
    if (!text.contains("\\u")) return text;
    StringBuilder result = new StringBuilder();
    boolean escape = false;
    for (int i = 0, length = text.length(); i < length; i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        if (escape) result.append("\\\\");
        escape = !escape;
      }
      else {
        if (!escape) {
          result.append(c);
        }
        else if (c != 'u') {
          result.append('\\').append(c);
          escape = false;
        }
        else {
          int startOfUnicodeEscape = i - 1;
          do {
            i++;
            if (i == length) {
              if (illegalEscapeConsumer != null) illegalEscapeConsumer.accept(startOfUnicodeEscape, i);
              return result.toString();
            }
            c = text.charAt(i);
          }
          while (c == 'u');
          int value = 0;
          for (int j = 0; j < 4; j++) {
            if (i + j >= length) {
              if (illegalEscapeConsumer != null) illegalEscapeConsumer.accept(startOfUnicodeEscape, i + j);
              return result.toString();
            }
            value <<= 4;
            c = text.charAt(i + j);
            if ('0' <= c && c <= '9') {
              value += c - '0';
            }
            else if ('a' <= c && c <= 'f') {
              value += (c - 'a') + 10;
            }
            else if ('A' <= c && c <= 'F') {
              value += (c - 'A') + 10;
            }
            else {
              if (illegalEscapeConsumer != null) illegalEscapeConsumer.accept(startOfUnicodeEscape, i + j);
              value = -1;
              break;
            }
          }
          if (value != -1) {
            i += 3;
            result.appendCodePoint(value);
          }
          escape = false;
        }
      }
    }
    return result.toString();
  }

  private static @NotNull TextRange calculateErrorRange(@NotNull String rawText, int start) {
    int end;
    if (rawText.charAt(start + 1) == 'u') {
      end = start + 2;
      while (rawText.charAt(end) == 'u') end++;
      end += 4;
    }
    else {
      end = start + 2;
    }
    return new TextRange(start, end);
  }
}
