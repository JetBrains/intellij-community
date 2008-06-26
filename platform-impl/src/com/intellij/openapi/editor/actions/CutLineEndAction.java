/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 6:29:03 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EmptyClipboardOwner;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class CutLineEndAction extends EditorAction {
  public CutLineEndAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document doc = editor.getDocument();
      if (doc.getLineCount() == 0) return;
      int caretOffset = editor.getCaretModel().getOffset();
      final int lineNumber = doc.getLineNumber(caretOffset);
      int lineEndOffset = doc.getLineEndOffset(lineNumber);

      if (caretOffset >= lineEndOffset) {
        DeleteLineAtCaretAction.deleteLineAtCaret(editor);
        return;
      }

      copyToClipboard(doc, caretOffset, lineEndOffset, dataContext, editor);

      final int lineStartOffset = doc.getLineStartOffset(lineNumber);
      if (StringUtil.isEmptyOrSpaces(doc.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString())) {
        DeleteLineAtCaretAction.deleteLineAtCaret(editor);
      }
      else {
        doc.deleteString(caretOffset, lineEndOffset);
      }
    }

    private static void copyToClipboard(final Document doc,
                                        int caretOffset,
                                        int lineEndOffset,
                                        DataContext dataContext,
                                        Editor editor) {
      String s = doc.getCharsSequence().subSequence(caretOffset, lineEndOffset).toString();

      s = StringUtil.convertLineSeparators(s, "\n");
      StringSelection contents = new StringSelection(s);

      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        Clipboard clipboard = editor.getComponent().getToolkit().getSystemClipboard();
        clipboard.setContents(contents, EmptyClipboardOwner.INSTANCE);
      }
      else {
        CopyPasteManager.getInstance().setContents(contents);
      }
    }
  }
}
