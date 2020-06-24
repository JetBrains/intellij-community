// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.IndentData;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.util.codeInsight.CommentUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class CommentUtil extends CommentUtilCore {
  private CommentUtil() { }

  public static IndentData getMinLineIndent(Document document, int line1, int line2, @NotNull PsiFile file) {
    CharSequence chars = document.getCharsSequence();
    IndentData minIndent = null;
    for (int line = line1; line <= line2; line++) {
      int lineStart = document.getLineStartOffset(line);
      int textStart = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (textStart >= document.getTextLength()) {
        textStart = document.getTextLength();
      }
      else {
        char c = chars.charAt(textStart);
        if (c == '\n' || c == '\r') continue; // empty line
      }
      IndentData indent = IndentData.createFrom(chars, lineStart, textStart, CodeStyle.getIndentOptions(file).TAB_SIZE);
      minIndent = IndentData.min(minIndent, indent);
    }
    if (minIndent == null && line1 == line2 && line1 < document.getLineCount() - 1) {
      return getMinLineIndent(document, line1 + 1, line1 + 1, file);
    }
    return minIndent;
  }
}
