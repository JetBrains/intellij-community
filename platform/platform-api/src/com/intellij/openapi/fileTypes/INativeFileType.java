package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public interface INativeFileType extends FileType {
  boolean openFileInAssociatedApplication(Project project, VirtualFile file);
  boolean useNativeIcon();
}
