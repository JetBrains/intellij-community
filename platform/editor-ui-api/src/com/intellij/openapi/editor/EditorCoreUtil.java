// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EditorCoreUtil {
  public static boolean isTrueSmoothScrollingEnabled() {
    return Boolean.getBoolean("idea.true.smooth.scrolling");
  }

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

    if (!buf.isEmpty()) {
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

  public static EditorHighlighter createEmptyHighlighter(@Nullable Project project, @NotNull Document document) {
    EditorHighlighter highlighter = new EmptyEditorHighlighter(new TextAttributes()) {
      @Override
      public @NotNull HighlighterIterator createIterator(int startOffset) {
        setText(document.getImmutableCharSequence());
        return super.createIterator(startOffset);
      }

      @Override
      public void setColorScheme(@NotNull EditorColorsScheme scheme) {
      }
    };
    highlighter.setEditor(new HighlighterClient() {
      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public void repaint(int start, int end) {
      }

      @Override
      public Document getDocument() {
        return document;
      }
    });
    return highlighter;
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

  public static boolean inVirtualSpace(@NotNull Editor editor, @NotNull LogicalPosition logicalPosition) {
    return !editor.offsetToLogicalPosition(editor.logicalPositionToOffset(logicalPosition)).equals(logicalPosition);
  }
}
