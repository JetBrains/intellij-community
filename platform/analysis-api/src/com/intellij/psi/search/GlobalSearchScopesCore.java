/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public class GlobalSearchScopesCore {
  @NotNull
  public static GlobalSearchScope projectProductionScope(@NotNull Project project) {
    return new ProductionScopeFilter(project);
  }

  @NotNull
  public static GlobalSearchScope projectTestScope(@NotNull Project project) {
    return new TestScopeFilter(project);
  }

  @NotNull
  public static GlobalSearchScope directoryScope(@NotNull PsiDirectory directory, final boolean withSubdirectories) {
    return new DirectoryScope(directory, withSubdirectories);
  }

  @NotNull
  public static GlobalSearchScope directoryScope(@NotNull Project project, @NotNull VirtualFile directory, final boolean withSubdirectories) {
    return new DirectoryScope(project, directory, withSubdirectories);
  }

  public static GlobalSearchScope filterScope(@NotNull Project project, @NotNull NamedScope set) {
    return new FilterScopeAdapter(project, set);
  }

  private static class FilterScopeAdapter extends GlobalSearchScope {
    private final NamedScope mySet;
    private final PsiManager myManager;

    private FilterScopeAdapter(@NotNull Project project, @NotNull NamedScope set) {
      super(project);
      mySet = set;
      myManager = PsiManager.getInstance(project);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      Project project = getProject();
      NamedScopesHolder holder = NamedScopeManager.getInstance(project);
      final PackageSet packageSet = mySet.getValue();
      if (packageSet != null) {
        if (packageSet instanceof PackageSetBase) return ((PackageSetBase)packageSet).contains(file, project, holder);
        PsiFile psiFile = myManager.findFile(file);
        return psiFile != null && packageSet.contains(psiFile, holder);
      }
      return false;
    }

    @Override
    public String getDisplayName() {
      return mySet.getName();
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;

    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true; //TODO (optimization?)
    }

    @Override
    public boolean isSearchInLibraries() {
      return true; //TODO (optimization?)
    }
  }

  private static class ProductionScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;

    private ProductionScopeFilter(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndex.isInSourceContent(file) && !myFileIndex.isInTestSourceContent(file);
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return !testSources;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    @Override
    public String getDisplayName() {
      return PsiBundle.message("psi.search.scope.production.files");
    }
  }

  private static class TestScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;

    private TestScopeFilter(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndex.isInTestSourceContent(file);
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return testSources;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    @Override
    public String getDisplayName() {
      return PsiBundle.message("psi.search.scope.test.files");
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

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myWithSubdirectories ? VfsUtilCore.isAncestor(myDirectory, file, false) : myDirectory.equals(file.getParent());
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "directory scope: " + myDirectory + "; withSubdirs:"+myWithSubdirectories;
    }
  }
}
