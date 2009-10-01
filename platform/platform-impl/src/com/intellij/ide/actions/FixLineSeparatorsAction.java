package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class FixLineSeparatorsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (project == null || vFiles == null) return;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        for (VirtualFile vFile : vFiles) {
          fixSeparators(vFile);
        }
      }
    }, "fixing line separators", null);
  }

  private static void fixSeparators(VirtualFile vFile) {
    if (vFile.isDirectory()) {
      for (VirtualFile child : vFile.getChildren()) {
        fixSeparators(child);
      }
    }
    else {
      if (vFile.getFileType().isBinary()) {
        return;
      }
      final Document document = FileDocumentManager.getInstance().getDocument(vFile);
      if (areSeparatorsBroken(document)) {
        fixSeparators(document);
      }
    }
  }

  private static boolean areSeparatorsBroken(Document document) {
    final int count = document.getLineCount();
    for (int i = 1; i < count; i += 2) {
      if (document.getLineStartOffset(i) != document.getLineEndOffset(i)) {
        return false;
      }
    }
    return true;    
  }

  private static void fixSeparators(final Document document) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        int i = 1;
        while(i < document.getLineCount()) {
          final int start = document.getLineEndOffset(i);
          final int end = document.getLineEndOffset(i) + document.getLineSeparatorLength(i);
          document.deleteString(start, end);
          i++;
        }
      }
    });
  }
}
