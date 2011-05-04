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

package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mike
 */
public class SelectWordUtil {
    
  private static ExtendWordSelectionHandler[] SELECTIONERS = new ExtendWordSelectionHandler[]{
  };

  private static boolean ourExtensionsLoaded = false;

  private SelectWordUtil() {
  }

  public static void registerSelectioner(ExtendWordSelectionHandler selectioner) {
    SELECTIONERS = ArrayUtil.append(SELECTIONERS, selectioner);
  }

  static ExtendWordSelectionHandler[] getExtendWordSelectionHandlers() {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      for (ExtendWordSelectionHandler handler : Extensions.getExtensions(ExtendWordSelectionHandler.EP_NAME)) {
        registerSelectioner(handler);        
      }
    }
    return SELECTIONERS;
  }

  public static void addWordSelection(boolean camel, CharSequence editorText, int cursorOffset, @NotNull List<TextRange> ranges) {
    TextRange camelRange = camel ? getCamelSelectionRange(editorText, cursorOffset) : null;
    if (camelRange != null) {
      ranges.add(camelRange);
    }

    TextRange range = getWordSelectionRange(editorText, cursorOffset);
    if (range != null && !range.equals(camelRange)) {
      ranges.add(range);
    }
  }

  @Nullable
  private static TextRange getCamelSelectionRange(CharSequence editorText, int cursorOffset) {
    if (cursorOffset < 0 || cursorOffset >= editorText.length()) {
      return null;
    }
    if (cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset + 1;
      final int textLen = editorText.length();

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        final char prevChar = editorText.charAt(start - 1);
        final char curChar = editorText.charAt(start);
        final char nextChar = start + 1 < textLen ? editorText.charAt(start + 1) : 0; // 0x00 is not lowercase.

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar == '_' && curChar != '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        start--;
      }

      while (end < textLen && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        final char prevChar = editorText.charAt(end - 1);
        final char curChar = editorText.charAt(end);
        final char nextChar = end + 1 < textLen ? editorText.charAt(end + 1) : 0; // 0x00 is not lowercase

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar != '_' && curChar == '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        end++;
      }

      if (start + 1 < end) {
        return new TextRange(start, end);
      }
    }

    return null;
  }

  @Nullable
  public static TextRange getWordSelectionRange(@NotNull CharSequence editorText, int cursorOffset) {
    int length = editorText.length();
    if (length == 0) return null;
    if (cursorOffset == length ||
        cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset;

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        start--;
      }

      while (end < length && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        end++;
      }

      return new TextRange(start, end);
    }

    return null;
  }

  public static void processRanges(@Nullable PsiElement element,
                                   CharSequence text,
                                   int cursorOffset,
                                   Editor editor,
                                   Processor<TextRange> consumer) {
    if (element == null) return;

    PsiFile file = element.getContainingFile();

    FileViewProvider viewProvider = file.getViewProvider();

    processInFile(element, consumer, text, cursorOffset, editor);

    for (PsiFile psiFile : viewProvider.getAllFiles()) {
      if (psiFile == file) continue;

      FileASTNode fileNode = psiFile.getNode();
      if (fileNode == null) continue;

      ASTNode nodeAt = fileNode.findLeafElementAt(element.getTextOffset());
      if (nodeAt == null) continue;

      PsiElement elementAt = nodeAt.getPsi();

      while (!(elementAt instanceof PsiFile) && elementAt != null) {
        if (elementAt.getTextRange().contains(element.getTextRange())) break;

        elementAt = elementAt.getParent();
      }

      if (elementAt == null) continue;

      processInFile(elementAt, consumer, text, cursorOffset, editor);
    }
  }

  private static void processInFile(@NotNull PsiElement element,
                                    Processor<TextRange> consumer,
                                    CharSequence text,
                                    int cursorOffset,
                                    Editor editor) {
    PsiElement e = element;
    while (e != null && !(e instanceof PsiFile)) {
      if (processElement(e, consumer, text, cursorOffset, editor)) return;
      e = e.getParent();
    }
  }

  private static boolean processElement(@NotNull PsiElement element,
                                     Processor<TextRange> processor,
                                     CharSequence text,
                                     int cursorOffset,
                                     Editor editor) {
    boolean stop = false;

    for (ExtendWordSelectionHandler selectioner : getExtendWordSelectionHandlers()) {
      if (!selectioner.canSelect(element)) continue;

      List<TextRange> ranges = selectioner.select(element, text, cursorOffset, editor);
      if (ranges == null) continue;

      for (TextRange range : ranges) {
        if (range == null) continue;

        stop |= processor.process(range);
      }
    }

    return stop;
  }

}
