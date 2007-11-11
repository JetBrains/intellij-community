/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProjectScope {
  private static final Key<GlobalSearchScope> ALL_SCOPE_KEY = new Key<GlobalSearchScope>("ALL_SCOPE_KEY");
  private static final Key<GlobalSearchScope> PROJECT_SCOPE_KEY = new Key<GlobalSearchScope>("PROJECT_SCOPE_KEY");

  private ProjectScope() {
  }

  public static GlobalSearchScope getAllScope(Project project) {
    GlobalSearchScope allScope = project.getUserData(ALL_SCOPE_KEY);

    if (allScope == null) {
      final ProjectRootManager projectRootManager = project.getComponent(ProjectRootManager.class);
      allScope = new GlobalSearchScope() {
        final ProjectFileIndex myProjectFileIndex = projectRootManager.getFileIndex();

        public boolean contains(VirtualFile file) {
          return true;
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          List<OrderEntry> entries1 = myProjectFileIndex.getOrderEntriesForFile(file1);
          List<OrderEntry> entries2 = myProjectFileIndex.getOrderEntriesForFile(file2);
          if (entries1.size() != entries2.size()) return 0;

          int res = 0;
          for (OrderEntry entry1 : entries1) {
            Module module = entry1.getOwnerModule();
            ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
            OrderEntry entry2 = moduleFileIndex.getOrderEntryForFile(file2);
            if (entry2 == null) {
              return 0;
            }
            else {
              int aRes = entry2.compareTo(entry1);
              if (aRes == 0) return 0;
              if (res == 0) {
                res = aRes;
              }
              else if (res != aRes) {
                return 0;
              }
            }
          }

          return res;
        }

        public boolean isSearchInModuleContent(@NotNull Module aModule) {
          return true;
        }

        public boolean isSearchInLibraries() {
          return true;
        }

        public String getDisplayName() {
          return PsiBundle.message("psi.search.scope.project.and.libraries");
        }

        @NotNull
        public GlobalSearchScope intersectWith(@NotNull final GlobalSearchScope scope) {
          return scope;
        }

        public GlobalSearchScope uniteWith(@NotNull final GlobalSearchScope scope) {
          return this;
        }

        public String toString() {
          return getDisplayName();
        }
      };

      project.putUserData(ALL_SCOPE_KEY, allScope);
    }
    return allScope;
  }

  public static GlobalSearchScope getProjectScope(Project project) {
    GlobalSearchScope projectScope = project.getUserData(PROJECT_SCOPE_KEY);
    if (projectScope == null) {
      final ProjectRootManager projectRootManager = project.getComponent(ProjectRootManager.class);
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
      project.putUserData(PROJECT_SCOPE_KEY, projectScope);
    }
    return projectScope;
  }
}