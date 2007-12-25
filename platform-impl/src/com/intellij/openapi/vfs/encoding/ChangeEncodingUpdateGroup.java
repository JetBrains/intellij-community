package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
    Pair<String, Boolean> result = update(project, virtualFile);
    e.getPresentation().setText(result.getFirst());
    e.getPresentation().setEnabled(result.getSecond());
  }

  public static Pair<String,Boolean> update(final Project project, final VirtualFile virtualFile) {
    boolean enabled = virtualFile != null && ChooseFileEncodingAction.isEnabled(project, virtualFile);
    String text;
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
      //no sense to reload file with UTF-detected chars using other encoding
      else if (LoadTextUtil.utfCharsetWasDetectedFromBytes(virtualFile)) {
        pattern = "Encoding: {0}";
        enabled = false;
      }
      else {
        pattern = "Change encoding from ''{0}'' to";
      }
      if (charset == null) charset = virtualFile.getCharset();
      text = MessageFormat.format(pattern, charset.displayName());
    }
    else {
      text = "Encoding";
    }

    return Pair.create(text, enabled);
  }
}
