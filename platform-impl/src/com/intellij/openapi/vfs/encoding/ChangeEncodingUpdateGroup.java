package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.editor.Document;

import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * @author cdr
 */
public class ChangeEncodingUpdateGroup extends DefaultActionGroup {
  public void update(final AnActionEvent e) {
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    boolean enabled = project != null && virtualFile != null;
    if (enabled) {
      String pattern;
      Charset charset = encodingFromContent(project, virtualFile);
      if (charset != null) {
        pattern = "Encoding: {0}";
        enabled = false;
      }
      else if (FileDocumentManager.getInstance().isFileModified(virtualFile)) {
        pattern = "Save ''{0}''-encoded file in";
      }
      else {
        pattern = "Change encoding from ''{0}'' to";
      }
      if (charset == null) charset = virtualFile.getCharset();
      e.getPresentation().setText(MessageFormat.format(pattern, charset.toString()));
    }
    else {
      e.getPresentation().setText("Encoding");
    }
    e.getPresentation().setEnabled(enabled);
  }
  private static Charset encodingFromContent(Project project, VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    if (fileType instanceof LanguageFileType) {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) return null;
      String text = document.getText();
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    return null;
  }
}
