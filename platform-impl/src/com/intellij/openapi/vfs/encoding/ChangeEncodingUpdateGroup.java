package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;

import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * @author cdr
 */
public class ChangeEncodingUpdateGroup extends DefaultActionGroup {
  public void update(final AnActionEvent e) {
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 1) {
      virtualFile = null;
    }
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (virtualFile != null){
      Navigatable navigatable = e.getData(PlatformDataKeys.NAVIGATABLE);
      if (navigatable instanceof OpenFileDescriptor) {
        // prefer source to the class file
        virtualFile = ((OpenFileDescriptor)navigatable).getFile();
      }
    }
    boolean enabled = virtualFile != null && ChooseFileEncodingAction.isEnabled(project, virtualFile);
    if (enabled) {
      String pattern;
      Charset charset = ChooseFileEncodingAction.encodingFromContent(project, virtualFile);
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
}
