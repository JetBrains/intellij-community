/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 10:47:00 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class UnindentSelectionAction extends EditorAction {
  public UnindentSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      Project project = DataKeys.PROJECT.getData(dataContext);
      unindentSelection(editor, project);
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return !editor.isOneLineMode() && !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }
  }

  private static void unindentSelection(Editor editor, Project project) {
    int oldSelectionStart = editor.getSelectionModel().getSelectionStart();
    int oldSelectionEnd = editor.getSelectionModel().getSelectionEnd();
    if(!editor.getSelectionModel().hasSelection()) {
      oldSelectionStart = editor.getCaretModel().getOffset();
      oldSelectionEnd = oldSelectionStart;
    }

    Document document = editor.getDocument();
    int startIndex = document.getLineNumber(oldSelectionStart);
    if(startIndex == -1) {
      startIndex = document.getLineCount() - 1;
    }
    int endIndex = document.getLineNumber(oldSelectionEnd);
    if(endIndex > 0 && document.getLineStartOffset(endIndex) == oldSelectionEnd && endIndex > startIndex) {
      endIndex --;
    }
    if(endIndex == -1) {
      endIndex = document.getLineCount() - 1;
    }

    if (startIndex < 0 || endIndex < 0) return;

    VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    final FileType fileType = vFile == null ? null : FileTypeManager.getInstance().getFileTypeByFile(vFile);

    int blockIndent = CodeStyleSettingsManager.getSettings(project).getIndentSize(fileType);
    IndentSelectionAction.doIndent(endIndex, startIndex, document, project, editor, -blockIndent);
  }
}
