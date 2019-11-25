// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class LightEditUtil {

  private LightEditUtil() {
  }

  private static final String ENABLED_FILE_OPEN_KEY = "light.edit.file.open.enabled";

  private static final String CLOSE_SAVE = ApplicationBundle.message("light.edit.close.save");
  private static final String CLOSE_DISCARD = ApplicationBundle.message("light.edit.close.discard");
  private static final String CLOSE_CANCEL = ApplicationBundle.message("light.edit.close.cancel");

  public static boolean openFile(@NotNull VirtualFile file) {
    if (Registry.is(ENABLED_FILE_OPEN_KEY)) {
      LightEditService.getInstance().openFile(file);
      return true;
    }
    return false;
  }

  public static boolean openFile(@NotNull Path path) {
    VirtualFile virtualFile = VfsUtil.findFile(path, true);
    if (virtualFile != null) {
      return openFile(virtualFile);
    }
    return false;
  }

  public static Project getProject() {
    return ProjectManager.getInstance().getDefaultProject();
  }

  static boolean confirmClose(@NotNull LightEditorInfo editorInfo) {
    final String[] options = {CLOSE_SAVE, CLOSE_DISCARD, CLOSE_CANCEL};
    int result = Messages.showDialog(
      getProject(),
      ApplicationBundle.message("light.edit.close.message"),
      ApplicationBundle.message("light.edit.close.title"),
      options, 0, Messages.getWarningIcon());
    if (result >= 0) {
      if (CLOSE_CANCEL.equals(options[result])) {
        return false;
      }
      else if (CLOSE_SAVE.equals(options[result])) {
        FileDocumentManager.getInstance().saveDocument(editorInfo.getEditor().getDocument());
      }
      return true;
    }
    else {
      return false;
    }
  }
}
