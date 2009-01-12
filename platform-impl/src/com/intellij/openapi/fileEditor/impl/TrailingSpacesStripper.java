package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.util.text.CharArrayUtil;

public final class TrailingSpacesStripper extends FileDocumentManagerAdapter {
  public void beforeDocumentSaving(final Document document) {
    if (!document.isWritable()) return;

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null) return;

    final boolean doStrip = !settings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    final boolean ensureEOL = settings.isEnsureNewLineAtEOF();
    if (!doStrip && !ensureEOL) return;

    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      public void run() {
        if (doStrip) {
          final boolean inChangedLinesOnly = !settings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
          ((DocumentEx)document).stripTrailingSpaces(inChangedLinesOnly);
        }

        if (ensureEOL) {
          final int lines = document.getLineCount();
          if (lines > 0) {
            int start = document.getLineStartOffset(lines - 1);
            int end = document.getLineEndOffset(lines - 1);
            if (start != end) {
              CharSequence content = document.getCharsSequence();
              if (CharArrayUtil.containsOnlyWhiteSpaces(content.subSequence(start, end))) {
                document.deleteString(start, end);
              }
              else {
                document.insertString(end, "\n");
              }
            }
          }
        }
      }
    });
  }
}
