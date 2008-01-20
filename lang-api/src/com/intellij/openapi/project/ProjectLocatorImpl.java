/*
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class ProjectLocatorImpl extends ProjectLocator {
  @Nullable
  public Project guessProjectForFile(final VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null) return null;
    final Project[] projects = projectManager.getOpenProjects();
    if (projects.length == 0) return null;
    if (projects.length == 1 && !projects[0].isDisposed()) return projects[0];

    for (Project project : projects) {
      if (!project.isDisposed() && ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
        return project;
      }
    }

    return !projects[0].isDisposed() ? projects[0] : null;
  }
}