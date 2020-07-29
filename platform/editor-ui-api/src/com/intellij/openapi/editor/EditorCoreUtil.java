// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class EditorCoreUtil {
  public static void indentLine(Project project, @NotNull Editor editor, int lineNumber, int indent, boolean shouldUseSmartTabs) {
    int caretOffset = editor.getCaretModel().getOffset();
    int newCaretOffset = indentLine(project, editor, lineNumber, indent, caretOffset, shouldUseSmartTabs);
    editor.getCaretModel().moveToOffset(newCaretOffset);
  }

  public static int indentLine(Project project, @NotNull Editor editor, int lineNumber, int indent, int caretOffset, boolean shouldUseSmartTabs) {
    EditorSettings editorSettings = editor.getSettings();
    int tabSize = editorSettings.getTabSize(project);
    Document document = editor.getDocument();
    CharSequence text = document.getImmutableCharSequence();
    int spacesEnd = 0;
    int lineStart = 0;
    int lineEnd = 0;
    int tabsEnd = 0;
    if (lineNumber < document.getLineCount()) {
      lineStart = document.getLineStartOffset(lineNumber);
      lineEnd = document.getLineEndOffset(lineNumber);
      spacesEnd = lineStart;
      boolean inTabs = true;
      for (; spacesEnd <= lineEnd; spacesEnd++) {
        if (spacesEnd == lineEnd) {
          break;
        }
        char c = text.charAt(spacesEnd);
        if (c != '\t') {
          if (inTabs) {
            inTabs = false;
            tabsEnd = spacesEnd;
          }
          if (c != ' ') break;
        }
      }
      if (inTabs) {
        tabsEnd = lineEnd;
      }
    }
    int newCaretOffset = caretOffset;
    if (newCaretOffset >= lineStart && newCaretOffset < lineEnd && spacesEnd == lineEnd) {
      spacesEnd = newCaretOffset;
      tabsEnd = Math.min(spacesEnd, tabsEnd);
    }
    int oldLength = getSpaceWidthInColumns(text, lineStart, spacesEnd, tabSize);
    tabsEnd = getSpaceWidthInColumns(text, lineStart, tabsEnd, tabSize);

    int newLength = oldLength + indent;
    if (newLength < 0) {
      newLength = 0;
    }
    tabsEnd += indent;
    if (tabsEnd < 0) tabsEnd = 0;
    if (!shouldUseSmartTabs) tabsEnd = newLength;
    StringBuilder buf = new StringBuilder(newLength);
    for (int i = 0; i < newLength;) {
      if (tabSize > 0 && editorSettings.isUseTabCharacter(project) && i + tabSize <= tabsEnd) {
        buf.append('\t');
        i += tabSize;
      }
      else {
        buf.append(' ');
        i++;
      }
    }

    int newSpacesEnd = lineStart + buf.length();
    if (newCaretOffset >= spacesEnd) {
      newCaretOffset += buf.length() - (spacesEnd - lineStart);
    }
    else if (newCaretOffset >= lineStart && newCaretOffset > newSpacesEnd) {
      newCaretOffset = newSpacesEnd;
    }

    if (buf.length() > 0) {
      if (spacesEnd > lineStart) {
        document.replaceString(lineStart, spacesEnd, buf.toString());
      }
      else {
        document.insertString(lineStart, buf.toString());
      }
    }
    else {
      if (spacesEnd > lineStart) {
        document.deleteString(lineStart, spacesEnd);
      }
    }

    return newCaretOffset;
  }

  private static int getSpaceWidthInColumns(CharSequence seq, int startOffset, int endOffset, int tabSize) {
    int result = 0;
    for (int i = startOffset; i < endOffset; i++) {
      if (seq.charAt(i) == '\t') {
        result = (result / tabSize + 1) * tabSize;
      }
      else {
        result++;
      }
    }
    return result;
  }
}
