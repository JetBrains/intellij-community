package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class UniqueNameEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public String getEditorTabTitle(Project project, VirtualFile file) {
    if (!UISettings.getInstance().SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES || DumbService.isDumb(project)) {
      return null;
    }
    return UniqueNameBuilder.getUniqueVirtualFilePath(project, file);
  }
}
