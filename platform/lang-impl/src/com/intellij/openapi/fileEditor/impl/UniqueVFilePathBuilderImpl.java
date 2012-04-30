package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.ProjectScope;

import java.io.File;
import java.util.Collection;

/**
 * @author yole
 */
public class UniqueVFilePathBuilderImpl extends UniqueVFilePathBuilder {
  @Override
  public String getUniqueVirtualFilePath(Project project, VirtualFile file) {
    final Collection<VirtualFile> filesWithSameName = FilenameIndex.getVirtualFilesByName(project, file.getName(),
                                                                                          ProjectScope.getProjectScope(project));
    if (filesWithSameName.size() > 1) {
      String path = project.getBasePath();
      path = path == null ? "" : FileUtil.toSystemIndependentName(path);
      UniqueNameBuilder<VirtualFile> builder = new UniqueNameBuilder<VirtualFile>(path, File.separator, 25);
      for (VirtualFile virtualFile: filesWithSameName) {
        builder.addPath(virtualFile, virtualFile.getPath());
      }
      return builder.getShortPath(file);
    }
    return null;
  }
}
