/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeInsightUtilCore extends FileModificationService {
  public static <T extends PsiElement> T findElementInRange(@NotNull PsiFile file,
                                                            int startOffset,
                                                            int endOffset,
                                                            @NotNull Class<T> klass,
                                                            @NotNull Language language) {
    return findElementInRange(file, startOffset, endOffset, klass, language, null);
  }

  private static <T extends PsiElement> T findElementInRange(@NotNull PsiFile file,
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

  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@NotNull T element) {
    return forcePsiPostprocessAndRestoreElement(element, false);
  }

  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@NotNull T element,
                                                                              boolean useFileLanguage) {
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

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder outChars, @Nullable int[] sourceOffsets) {
    return parseStringCharacters(chars, outChars, sourceOffsets, true, true, '"', '\'');
  }

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder outChars, @Nullable int[] sourceOffsets, boolean slashMustBeEscaped, boolean exitOnEscapingWrongSymbol, @NotNull char... endChars) {
    assert sourceOffsets == null || sourceOffsets.length == chars.length()+1;
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      if (sourceOffsets != null) {
        for (int i = 0; i < sourceOffsets.length; i++) {
          sourceOffsets[i] = i;
        }
      }
      return true;
    }
    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index - 1;
        sourceOffsets[outChars.length() + 1 -outOffset] = index;
      }
      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
      c = chars.charAt(index++);
      switch (c) {
        case'b':
          outChars.append('\b');
          break;

        case't':
          outChars.append('\t');
          break;

        case'n':
          outChars.append('\n');
          break;

        case'f':
          outChars.append('\f');
          break;

        case'r':
          outChars.append('\r');
          break;

        case'\\':
          outChars.append('\\');
          break;

        case'0':
        case'1':
        case'2':
        case'3':
        case'4':
        case'5':
        case'6':
        case'7':
          char startC = c;
          int v = (int)c - '0';
          if (index < chars.length()) {
            c = chars.charAt(index++);
            if ('0' <= c && c <= '7') {
              v <<= 3;
              v += c - '0';
              if (startC <= '3' && index < chars.length()) {
                c = chars.charAt(index++);
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
          outChars.append((char)v);
          break;

        case'u':
          // uuuuu1234 is valid too
          while (index != chars.length() && chars.charAt(index) == 'u') {
            index++;
          }
          if (index + 4 <= chars.length()) {
            try {
              int code = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (code == 0x000a || code == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)code);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;

        default:
          if (CharArrayUtil.indexOf(endChars, c, 0, endChars.length) != -1) {
            outChars.append(c);
          }
          else if (!exitOnEscapingWrongSymbol) {
            if (!slashMustBeEscaped) {
              outChars.append('\\');
              if (sourceOffsets != null) {
                sourceOffsets[outChars.length() - outOffset] = index - 1;
              }
            }
            outChars.append(c);
          }
          else {
            return false;
          }
      }
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length() - outOffset] = index;
      }
    }
    return true;
  }

  /**
   * Maps the substring range inside Java String literal value back into the source code range.
   *
   * @param text string literal as present in source code (including quotes)
   * @param from start offset inside the represented string
   * @param to end offset inside the represented string
   * @return the range which represents the corresponding substring inside source representation,
   * or null if from/to values are out of bounds.
   */
  @Nullable
  public static TextRange mapBackStringRange(@NotNull String text, int from, int to) {
    if (from > to || to < 0) return null;
    if (text.startsWith("`")) {
      // raw string
      return new TextRange(from + 1, to + 1);
    }
    if (!text.startsWith("\"")) {
      return null;
    }
    if (text.indexOf('\\') == -1) {
      return new TextRange(from + 1, to + 1);
    }
    int curOffset = 0;
    int mappedFrom = -1, mappedTo = -1;
    int end = text.length() - 1;
    int i = 1;
    while (i <= end) {
      if (curOffset == from) {
        mappedFrom = i;
      }
      if (curOffset == to) {
        mappedTo = i;
        break;
      }
      if (i == end) break;
      char c = text.charAt(i++);
      if (c == '\\') {
        if (i == end) return null;
        // like \u0020
        char c1 = text.charAt(i++);
        if (c1 == 'u') {
          while (i < end && text.charAt(i) == 'u') i++;
          i += 4;
        } else if (c1 >= '0' && c1 <= '7') { // octal escape
          char c2 = i < end ? text.charAt(i) : 0;
          if (c2 >= '0' && c2 <= '7') {
            i++;
            char c3 = i < end ? text.charAt(i) : 0;
            if (c3 >= '0' && c3 <= '7' && c1 <= '3') {
              i++;
            }
          }
        }
      }
      curOffset++;
    }
    if (mappedFrom >= 0 && mappedTo >= 0) {
      return new TextRange(mappedFrom, mappedTo);
    }
    return null;
  }
}
