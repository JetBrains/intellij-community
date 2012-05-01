package com.intellij.openapi.fileEditor;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public abstract class UniqueVFilePathBuilder {
  private static final UniqueVFilePathBuilder DUMMY_BUILDER = new UniqueVFilePathBuilder() {
    @Override
    public String getUniqueVirtualFilePath(Project project, VirtualFile vFile) {
      return vFile.getPresentableName();
    }
  };

  public static UniqueVFilePathBuilder getInstance() {
    final UniqueVFilePathBuilder service = ServiceManager.getService(UniqueVFilePathBuilder.class);
    if (service == null) {
      return DUMMY_BUILDER;
    }
    return service;
  }

  public abstract String getUniqueVirtualFilePath(Project project, VirtualFile vFile);
}
