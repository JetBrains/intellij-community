/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:40:40 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;

public class SelectWordAtCaretAction extends TextComponentEditorAction {
  public SelectWordAtCaretAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      int caretOffset = editor.getCaretModel().getOffset();
      Document document = editor.getDocument();
      if (lineNumber >= document.getLineCount()) {
        return;
      }
      CharSequence text = document.getCharsSequence();

      boolean camel = editor.getSettings().isCamelWords();
      List<TextRange> ranges = new ArrayList<TextRange>();

      int textLength = document.getTextLength();
      if (caretOffset == textLength) caretOffset--;
      if (caretOffset < 0) return;

      SelectWordUtil.addWordSelection(camel, text, caretOffset, ranges);

      if (ranges.isEmpty()) return;

      int startWordOffset = Math.max(0, ranges.get(0).getStartOffset());
      int endWordOffset = Math.min(ranges.get(0).getEndOffset(), document.getTextLength());

      if (camel && ranges.size() == 2 && editor.getSelectionModel().getSelectionStart() == startWordOffset &&
          editor.getSelectionModel().getSelectionEnd() == endWordOffset) {
        startWordOffset = Math.max(0, ranges.get(1).getStartOffset());
        endWordOffset = Math.min(ranges.get(1).getEndOffset(), document.getTextLength());
      }

      editor.getSelectionModel().setSelection(startWordOffset, endWordOffset);
    }
  }
}
