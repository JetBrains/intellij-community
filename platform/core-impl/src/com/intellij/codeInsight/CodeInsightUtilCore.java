// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

public abstract class CodeInsightUtilCore extends FileModificationService {

  private static final Logger LOG = Logger.getInstance(CodeInsightUtilCore.class);

  public static <T extends PsiElement> T findElementInRange(@NotNull PsiFile file,
                                                            int startOffset,
                                                            int endOffset,
                                                            @NotNull Class<T> klass,
                                                            @NotNull Language language) {
    return findElementInRange(file, startOffset, endOffset, klass, language, null);
  }

  private static @Nullable <T extends PsiElement> T findElementInRange(@NotNull PsiFile file,
                                                                       int startOffset,
                                                                       int endOffset,
                                                                       @NotNull Class<T> klass,
                                                                       @NotNull Language language,
                                                                       @Nullable PsiElement initialElement) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, language);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    }
    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    final T element =
      ReflectionUtil.isAssignable(klass, commonParent.getClass())
      ? (T)commonParent : PsiTreeUtil.getParentOfType(commonParent, klass);

    if (element == initialElement) {
      return element;
    }
    
    if (element == null || element.getTextRange().getStartOffset() != startOffset || element.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return element;
  }

  public static @Nullable <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@NotNull T element) {
    return forcePsiPostprocessAndRestoreElement(element, false);
  }

  public static @Nullable <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@NotNull T element, boolean useFileLanguage) {
    final PsiFile psiFile = element.getContainingFile();
    final Document document = psiFile.getViewProvider().getDocument();
    //if (document == null) return element;
    final Language language = useFileLanguage ? psiFile.getLanguage() : PsiUtilCore.getDialect(element);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    final RangeMarker rangeMarker = document.createRangeMarker(element.getTextRange());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);

    T elementInRange = findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                          (Class<? extends T>)element.getClass(),
                                          language, element);
    rangeMarker.dispose();
    return elementInRange;
  }

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder out, int @Nullable [] sourceOffsets) {
    return parseStringCharacters(chars, out, sourceOffsets, true);
  }

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder out, int @Nullable [] sourceOffsets,
                                              boolean textBlock) {
    LOG.assertTrue(sourceOffsets == null || sourceOffsets.length == chars.length() + 1);
    if (noEscape(chars, sourceOffsets)) {
      out.append(chars);
      return true;
    }
    return parseStringCharactersWithEscape(chars, textBlock, out, sourceOffsets);
  }

  /**
   * Parse Java String literal and get the result, if literal is valid
   * @param chars         String literal source
   * @param sourceOffsets optional output parameter:
   *                      sourceOffsets[i in returnValue.indices] represents
   *                      the index of returnValue[i] character in the source literal
   * @return String literal value, or null, if the literal is invalid
   */
  public static @Nullable CharSequence parseStringCharacters(@NotNull String chars, int @Nullable [] sourceOffsets) {
    LOG.assertTrue(sourceOffsets == null || sourceOffsets.length == chars.length() + 1);
    if (noEscape(chars, sourceOffsets)) {
      return chars;
    }
    StringBuilder out = new StringBuilder(chars.length());
    return parseStringCharactersWithEscape(chars, true, out, sourceOffsets) ? out : null;
  }

  private static boolean noEscape(@NotNull String chars, int @Nullable [] sourceOffsets) {
    if (chars.indexOf('\\') >= 0) return false;
    if (sourceOffsets != null) Arrays.setAll(sourceOffsets, IntUnaryOperator.identity());
    return true;
  }

  static boolean parseStringCharactersWithEscape(@NotNull String chars,
                                                 boolean textBlock,
                                                 @NotNull StringBuilder out,
                                                 int @Nullable [] sourceOffsets) {
    int index = 0;
    final int outOffset = out.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[out.length() - outOffset] = index - 1;
        sourceOffsets[out.length() + 1 - outOffset] = index;
      }
      if (c != '\\') {
        out.append(c);
        continue;
      }
      index = parseEscapedSymbol(false, chars, index, textBlock, out);
      if (index == -1) return false;
      if (sourceOffsets != null) {
        sourceOffsets[out.length() - outOffset] = index;
      }
    }
    return true;
  }

  private static int parseEscapedSymbol(boolean isAfterEscapedBackslash, @NotNull String chars, int index,
                                        boolean textBlock,
                                        @NotNull StringBuilder out) {
    if (index == chars.length()) return -1;
    char c = chars.charAt(index++);
    if (parseEscapedChar(c, textBlock, out)) {
      return index;
    }
    switch (c) {
      case '\\':
        boolean isUnicodeSequenceStart = isAfterEscapedBackslash && index < chars.length() && chars.charAt(index) == 'u';
        if (isUnicodeSequenceStart) {
          index = parseUnicodeEscape(true, chars, index, textBlock, out);
        }
        else {
          out.append('\\');
        }
        break;

      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
        index = parseOctalEscape(c, chars, index, out);
        break;

      case 'u':
        if (isAfterEscapedBackslash) return -1;
        index = parseUnicodeEscape(false, chars, index - 1, textBlock, out);
        break;

      default:
        return -1;
    }
    return index;
  }

  private static int parseUnicodeEscape(boolean isAfterEscapedBackslash, @NotNull String s, int index,
                                        boolean textBlock,
                                        @NotNull StringBuilder out) {
    int len = s.length();
    // uuuuu1234 is valid too
    do {
      index++;
    }
    while (index < len && s.charAt(index) == 'u');
    if (index + 4 > len) return -1;
    try {
      char c = s.charAt(index);
      if (c == '+' || c == '-') return -1;
      int code = Integer.parseInt(s.substring(index, index + 4), 16);
      // unicode escaped line separators are invalid here when not a text block
      if (!textBlock && (code == 0x000a || code == 0x000d)) return -1;
      char escapedChar = (char)code;
      if (escapedChar == '\\') {
        if (isAfterEscapedBackslash) {
          // \u005c\u005c
          out.append('\\');
          return index + 4;
        }
        else {
          // u005cxyz
          return parseEscapedSymbol(true, s, index + 4, textBlock, out);
        }
      }
      if (isAfterEscapedBackslash) {
        // e.g. \u005c\u006e is converted to newline
        if (parseEscapedChar(escapedChar, textBlock, out)) return index + 4;
        return -1;
      }
      // just single unicode escape sequence
      out.append(escapedChar);
      return index + 4;
    }
    catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private static boolean parseEscapedChar(char c, boolean textBlock, @NotNull StringBuilder out) {
    switch (c) {
      case 'b':
        out.append('\b');
        return true;

      case 't':
        out.append('\t');
        return true;

      case 'n':
        out.append('\n');
        return true;

      case 'f':
        out.append('\f');
        return true;

      case 'r':
        out.append('\r');
        return true;

      case 's':
        out.append(' ');
        return true;

      case '\'':
        out.append('\'');
        return true;

      case '\"':
        out.append('"');
        return true;

      case '\n':
        return textBlock; // escaped newline only valid inside text block
    }
    return false;
  }

  private static int parseOctalEscape(char c, @NotNull String s, int index, @NotNull StringBuilder out) {
    char startC = c;
    int v = c - '0';
    if (index < s.length()) {
      c = s.charAt(index++);
      if ('0' <= c && c <= '7') {
        v <<= 3;
        v += c - '0';
        if (startC <= '3' && index < s.length()) {
          c = s.charAt(index++);
          if ('0' <= c && c <= '7') {
            v <<= 3;
            v += c - '0';
          }
          else {
            index--;
          }
        }
      }
      else {
        index--;
      }
    }
    out.append((char)v);
    return index;
  }
}
