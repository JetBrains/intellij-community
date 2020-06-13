// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;

public final class ConvertIndentsUtil {
  private static final IndentBuilder tabIndentBuilder = new IndentBuilder() {
    @Override
    public String buildIndent(int length, int tabSize) {
      return StringUtil.repeatSymbol('\t', length / tabSize) + StringUtil.repeatSymbol(' ', length % tabSize);
    }
  };
  private static final IndentBuilder spaceIndentBuilder = new IndentBuilder() {
    @Override
    public String buildIndent(int length, int tabSize) {
      return StringUtil.repeatSymbol(' ', length);
    }
  };

  public static int convertIndentsToTabs(Document document, int tabSize, TextRange textRange) {
    return processIndents(document, tabSize, textRange, tabIndentBuilder);
  }

  public static int convertIndentsToSpaces(Document document, int tabSize, TextRange textRange) {
    return processIndents(document, tabSize, textRange, spaceIndentBuilder);
  }

  private static int processIndents(Document document, int tabSize, TextRange textRange, IndentBuilder indentBuilder) {
    int[] changedLines = {0};
    DocumentUtil.executeInBulk(document, true, () -> {
      int startLine = document.getLineNumber(textRange.getStartOffset());
      int endLine = document.getLineNumber(textRange.getEndOffset());
      for (int line = startLine; line <= endLine; line++) {
        int indent = 0;
        final int lineStart = document.getLineStartOffset(line);
        final int lineEnd = document.getLineEndOffset(line);
        int indentEnd = lineEnd;
        for(int offset = Math.max(lineStart, textRange.getStartOffset()); offset < lineEnd; offset++) {
          char c = document.getCharsSequence().charAt(offset);
          if (c == ' ') {
            indent++;
          }
          else if (c == '\t') {
            indent = ((indent / tabSize) + 1) * tabSize;
          }
          else {
            indentEnd = offset;
            break;
          }
        }
        if (indent > 0) {
          String oldIndent = document.getCharsSequence().subSequence(lineStart, indentEnd).toString();
          String newIndent = indentBuilder.buildIndent(indent, tabSize);
          if (!oldIndent.equals(newIndent)) {
            document.replaceString(lineStart, indentEnd, newIndent);
            changedLines[0]++;
          }
        }
      }
    });
    return changedLines[0];
  }

  private interface IndentBuilder {
    String buildIndent(int length, int tabSize);
  }
}
