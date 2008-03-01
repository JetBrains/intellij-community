package com.intellij.platform;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ProjectBaseDirectory {
  public static ProjectBaseDirectory getInstance(Project project) {
    return ServiceManager.getService(project, ProjectBaseDirectory.class);
  }

  public VirtualFile BASE_DIR;

  public VirtualFile getBaseDir(final VirtualFile baseDir) {
    if (BASE_DIR != null) {
      return BASE_DIR;
    }
    return baseDir;
  }
}
