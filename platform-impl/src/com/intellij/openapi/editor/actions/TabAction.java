/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 9:51:34 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class TabAction extends EditorAction {
  public TabAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.EDIT_COMMAND_GROUP);
      CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.command.name"));
      Project project = DataKeys.PROJECT.getData(dataContext);
      insertTabAtCaret(editor, project);
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return !editor.isOneLineMode() && !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }
  }

  private static void insertTabAtCaret(Editor editor, Project project) {
    int columnNumber = editor.getCaretModel().getLogicalPosition().column;

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

    final Document doc = editor.getDocument();
    VirtualFile vFile = FileDocumentManager.getInstance().getFile(doc);
    final FileType fileType = vFile == null ? null : FileTypeManager.getInstance().getFileTypeByFile(vFile);

    int tabSize = settings.getIndentSize(fileType);
    int spacesToAddCount = tabSize - columnNumber % tabSize;

    boolean useTab = editor.getSettings().isUseTabCharacter(project);

    CharSequence chars = doc.getCharsSequence();
    if (useTab && settings.isSmartTabs(fileType)) {
      int offset = editor.getCaretModel().getOffset();
      while (offset > 0) {
        offset--;
        if (chars.charAt(offset) == '\t') continue;
        if (chars.charAt(offset) == '\n') break;
        useTab = false;
        break;
      }
    }

    doc.startGuardedBlockChecking();
    try {
      if(useTab) {
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, "\t", false);
      }
      else {
        StringBuffer buffer = new StringBuffer();
        for(int i=0; i<spacesToAddCount; i++) {
          buffer.append(' ');
        }
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, buffer.toString(), false);
      }
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
    }
    finally {
      doc.stopGuardedBlockChecking();
    }
  }
}
