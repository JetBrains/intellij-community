/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;

public class ProjectScope {
  private static final Key<GlobalSearchScope> ALL_SCOPE_KEY = new Key<GlobalSearchScope>("ALL_SCOPE_KEY");
  private static final Key<GlobalSearchScope> PROJECT_SCOPE_KEY = new Key<GlobalSearchScope>("PROJECT_SCOPE_KEY");

  private ProjectScope() {
  }

  public static GlobalSearchScope getAllScope(Project project) {
    GlobalSearchScope allScope = project.getUserData(ALL_SCOPE_KEY);

    if (allScope == null) {
      final ProjectRootManager projectRootManager = project.getComponent(ProjectRootManager.class);
      if (projectRootManager == null) {
        allScope = new EverythingGlobalScope();
      }
      else {
        allScope = new ProjectAndLibrariesScope(projectRootManager);
      }

      project.putUserData(ALL_SCOPE_KEY, allScope);
    }
    return allScope;
  }

  public static GlobalSearchScope getProjectScope(Project project) {
    GlobalSearchScope projectScope = project.getUserData(PROJECT_SCOPE_KEY);
    if (projectScope == null) {
      final ProjectRootManager projectRootManager = project.getComponent(ProjectRootManager.class);
      if (projectRootManager == null) {
        projectScope = new EverythingGlobalScope() {
          public boolean isSearchInLibraries() {
            return false;
          }
        };
      }
      else {
        projectScope = new GlobalSearchScope() {
          private final ProjectFileIndex myFileIndex = projectRootManager.getFileIndex();

          public boolean contains(VirtualFile file) {
            return file instanceof VirtualFileWindow || myFileIndex.isInContent(file);
          }

          public int compare(VirtualFile file1, VirtualFile file2) {
            return 0;
          }

          public boolean isSearchInModuleContent(@NotNull Module aModule) {
            return true;
          }

          public boolean isSearchInLibraries() {
            return false;
          }

          public String getDisplayName() {
            return PsiBundle.message("psi.search.scope.project");
          }

          public String toString() {
            return getDisplayName();
          }
        };
      }
      project.putUserData(PROJECT_SCOPE_KEY, projectScope);
    }
    return projectScope;
  }

}