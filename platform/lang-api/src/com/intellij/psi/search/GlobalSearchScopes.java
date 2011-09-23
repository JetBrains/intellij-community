/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class GlobalSearchScopes {
  private GlobalSearchScopes() {
  }

  public static GlobalSearchScope projectProductionScope(@NotNull Project project) {
    return new GlobalSearchScope.IntersectionScope(GlobalSearchScope.projectScope(project),
                                 new ProductionScopeFilter(project),
                                 PsiBundle.message("psi.search.scope.production.files"));
  }

  public static GlobalSearchScope projectTestScope(@NotNull Project project) {
    return new GlobalSearchScope.IntersectionScope(GlobalSearchScope.projectScope(project),
                                 new TestScopeFilter(project),
                                 PsiBundle.message("psi.search.scope.test.files"));
  }

  public static GlobalSearchScope directoryScope(@NotNull PsiDirectory directory, final boolean withSubdirectories) {
    return new DirectoryScope(directory, withSubdirectories);
  }

  public static GlobalSearchScope directoryScope(@NotNull Project project, @NotNull VirtualFile directory, final boolean withSubdirectories) {
    return new DirectoryScope(project, directory, withSubdirectories);
  }

  private static class FilterScopeAdapter extends GlobalSearchScope {
    private final NamedScope mySet;
    private final PsiManager myManager;

    private FilterScopeAdapter(@NotNull Project project, @NotNull NamedScope set) {
      super(project);
      mySet = set;
      myManager = PsiManager.getInstance(project);
    }

    public boolean contains(VirtualFile file) {
      NamedScopesHolder holder = NamedScopeManager.getInstance(getProject());
      final PackageSet packageSet = mySet.getValue();
      if (packageSet != null) {
        if (packageSet instanceof PackageSetBase) return ((PackageSetBase)packageSet).contains(file, holder);
        PsiFile psiFile = myManager.findFile(file);
        if (psiFile == null) return false;
        return packageSet.contains(psiFile, holder);
      }
      return false;
    }

    public String getDisplayName() {
      return mySet.getName();
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;

    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true; //TODO (optimization?)
    }

    public boolean isSearchInLibraries() {
      return true; //TODO (optimization?)
    }
  }

  public static GlobalSearchScope filterScope(@NotNull Project project, @NotNull NamedScope set) {
    return new FilterScopeAdapter(project, set);
  }

  private static class ProductionScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;

    private ProductionScopeFilter(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean contains(VirtualFile file) {
      return myFileIndex.isInSourceContent(file) && !myFileIndex.isInTestSourceContent(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return !testSources;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  }

  private static class TestScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;

    private TestScopeFilter(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean contains(VirtualFile file) {
      return myFileIndex.isInTestSourceContent(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return testSources;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  }

  private static class DirectoryScope extends GlobalSearchScope {
    private final VirtualFile myDirectory;
    private final boolean myWithSubdirectories;

    private DirectoryScope(@NotNull PsiDirectory directory, final boolean withSubdirectories) {
      super(directory.getProject());
      myWithSubdirectories = withSubdirectories;
      myDirectory = directory.getVirtualFile();
    }

    private DirectoryScope(@NotNull Project project, @NotNull VirtualFile directory, final boolean withSubdirectories) {
      super(project);
      myWithSubdirectories = withSubdirectories;
      myDirectory = directory;
    }

    public boolean contains(VirtualFile file) {
      if (myWithSubdirectories) {
        return VfsUtilCore.isAncestor(myDirectory, file, false);
      }
      else {
        return myDirectory.equals(file.getParent());
      }
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

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "directory scope: " + myDirectory + "; withSubdirs:"+myWithSubdirectories;
    }
  }
}
