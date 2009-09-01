package com.intellij.openapi.wm;

import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class IdeaFrameTitleBuilder extends FrameTitleBuilder {
  public String getProjectTitle(final Project project) {
    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir != null) {
      return project.getName() + " - [" + baseDir.getPresentableUrl() + "]";
    }
    return project.getName();
  }

  public String getFileTitle(final Project project, final VirtualFile file) {
    return ProjectUtil.calcRelativeToProjectPath(file, project);
  }
}
