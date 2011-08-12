package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.ProjectScope;

import java.io.File;

/**
 * @author yole
 */
public class UniqueNameEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public String getEditorTabTitle(Project project, VirtualFile file) {
    if (!UISettings.getInstance().SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES || DumbService.isDumb(project)) {
      return null;
    }
    VirtualFile parent = file.getParent();
    if (parent == null) {
      return null;
    }
    final PsiFile[] filesWithSameName = FilenameIndex.getFilesByName(project, file.getName(), ProjectScope.getProjectScope(project));
    if (filesWithSameName.length > 1) {
      return parent.getName() + File.separator + file.getPresentableName();
    }
    return null;
  }
}
