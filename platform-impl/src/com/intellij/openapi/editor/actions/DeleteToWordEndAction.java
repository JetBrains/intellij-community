/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:18:30 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class DeleteToWordEndAction extends TextComponentEditorAction {
  public DeleteToWordEndAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      deleteToWordEnd(editor);
    }
  }

  private static void deleteToWordEnd(Editor editor) {
    int startOffset = editor.getCaretModel().getOffset();
    int endOffset = getWordEndOffset(editor, startOffset);
    if(endOffset > startOffset) {
      Document document = editor.getDocument();
      document.deleteString(startOffset, endOffset);
    }
  }

  private static int getWordEndOffset(Editor editor, int offset) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    if(offset >= document.getTextLength() - 1)
      return offset;
    int newOffset = offset + 1;
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if(newOffset > maxOffset) {
      if(lineNumber+1 >= document.getLineCount())
        return offset;
      maxOffset = document.getLineEndOffset(lineNumber+1);
    }
    boolean camel = editor.getSettings().isCamelWords();
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordEnd(text, newOffset, camel) ||
          EditorActionUtil.isWordStart(text, newOffset, camel)) {
        break;
      }
    }
    return newOffset;
  }
}
